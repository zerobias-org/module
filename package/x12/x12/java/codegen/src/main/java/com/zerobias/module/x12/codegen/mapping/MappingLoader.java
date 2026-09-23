package com.zerobias.module.x12.codegen.mapping;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the pyx12 mapping XML from the classpath ({@code mapping/...} inside the
 * {@code com.imsweb:x12-parser} jar) into {@link Mapping} records.
 *
 * <p>Two on-disk dialects exist in imsweb 1.16: the original {@code map.xsd}
 * form (child elements: {@code <name>}, {@code <usage>}, {@code <pos>}, ...) and
 * the {@code map.v2.xsd} form (the same values as attributes). Every accessor
 * here tries the attribute first and the direct child element second, so both
 * load identically. Comments and whitespace are ignored; only direct children
 * are consulted (a loop's {@code <name>} is never confused with a nested
 * segment's).
 */
public final class MappingLoader {

    /** Classpath directory the imsweb jar keeps the maps under. */
    public static final String RESOURCE_DIR = "mapping/";

    private final ClassLoader loader;

    public MappingLoader() {
        this(MappingLoader.class.getClassLoader());
    }

    public MappingLoader(ClassLoader loader) {
        this.loader = loader;
    }

    /** Load a transaction map by file name, e.g. {@code 835.5010.X221.A1.xml}. */
    public Mapping.Transaction loadTransaction(String fileName) {
        final Element root = parse(fileName).getDocumentElement();
        if (!"transaction".equals(root.getTagName())) {
            throw new IllegalStateException(fileName + ": root is <" + root.getTagName() + ">, expected <transaction>");
        }
        final Element loopEl = firstChild(root, "loop");
        if (loopEl == null) {
            throw new IllegalStateException(fileName + ": <transaction> has no <loop>");
        }
        return new Mapping.Transaction(root.getAttribute("xid"), attrOrChild(root, "name"), readLoop(loopEl));
    }

    /** {@code dataele.xml} → data element number → definition. */
    public Map<String, Mapping.DataElement> loadDataElements() {
        final Element root = parse("dataele.xml").getDocumentElement();
        final Map<String, Mapping.DataElement> out = new LinkedHashMap<>();
        for (Element e : children(root, "data_ele")) {
            final String num = e.getAttribute("ele_num");
            out.put(num, new Mapping.DataElement(num, e.getAttribute("data_type"),
                intOrNull(e.getAttribute("min_len")), intOrNull(e.getAttribute("max_len")), e.getAttribute("name")));
        }
        return out;
    }

    /** {@code codes.xml} → codeset id → codeset. */
    public Map<String, Mapping.CodeSet> loadCodeSets() {
        final Element root = parse("codes.xml").getDocumentElement();
        final Map<String, Mapping.CodeSet> out = new LinkedHashMap<>();
        for (Element cs : children(root, "codeset")) {
            final List<String> codes = new ArrayList<>();
            for (Element version : children(cs, "version")) {
                for (Element c : children(version, "code")) {
                    codes.add(c.getTextContent().trim());
                }
            }
            final String id = attrOrChild(cs, "id");
            out.put(id, new Mapping.CodeSet(id, attrOrChild(cs, "name"), blankToNull(attrOrChild(cs, "data_ele")), codes));
        }
        return out;
    }

    // ---- tree readers -------------------------------------------------------

    private Mapping.Loop readLoop(Element el) {
        final List<Mapping.Structure> children = new ArrayList<>();
        for (Element c : elementChildren(el)) {
            switch (c.getTagName()) {
                case "loop" -> children.add(readLoop(c));
                case "segment" -> children.add(readSegment(c));
                default -> { /* name/usage/pos/repeat metadata */ }
            }
        }
        return new Mapping.Loop(el.getAttribute("xid"), attrOrChild(el, "name"), attrOrChild(el, "usage"),
            attrOrChild(el, "pos"), attrOrChild(el, "repeat"), blankToNull(el.getAttribute("type")), children);
    }

    private Mapping.Segment readSegment(Element el) {
        final List<Mapping.Field> fields = new ArrayList<>();
        final List<String> syntax = new ArrayList<>();
        for (Element c : elementChildren(el)) {
            switch (c.getTagName()) {
                case "element" -> fields.add(readElement(c));
                case "composite" -> fields.add(readComposite(c));
                case "syntax" -> syntax.add(c.getTextContent().trim());
                default -> { /* metadata */ }
            }
        }
        return new Mapping.Segment(el.getAttribute("xid"), attrOrChild(el, "name"), attrOrChild(el, "usage"),
            attrOrChild(el, "pos"), attrOrChild(el, "max_use"), blankToNull(attrOrChild(el, "end_tag")), syntax, fields);
    }

    private Mapping.Composite readComposite(Element el) {
        final List<Mapping.Element> elements = new ArrayList<>();
        for (Element c : children(el, "element")) {
            elements.add(readElement(c));
        }
        return new Mapping.Composite(el.getAttribute("xid"), attrOrChild(el, "data_ele"), attrOrChild(el, "name"),
            attrOrChild(el, "usage"), seq(el), intOrNull(attrOrChild(el, "repeat")), elements);
    }

    private Mapping.Element readElement(Element el) {
        final List<String> codes = new ArrayList<>();
        String external = null;
        final Element vc = firstChild(el, "valid_codes");
        if (vc != null) {
            external = blankToNull(vc.getAttribute("external"));
            for (Element c : children(vc, "code")) {
                codes.add(c.getTextContent().trim());
            }
        }
        return new Mapping.Element(el.getAttribute("xid"), attrOrChild(el, "data_ele"), attrOrChild(el, "name"),
            attrOrChild(el, "usage"), seq(el), intOrNull(attrOrChild(el, "repeat")), codes, external,
            blankToNull(attrOrChild(el, "regex")));
    }

    private static int seq(Element el) {
        final String s = attrOrChild(el, "seq");
        if (s == null || s.isBlank()) {
            throw new IllegalStateException("<" + el.getTagName() + " xid=" + el.getAttribute("xid") + "> has no seq");
        }
        return Integer.parseInt(s.trim());
    }

    // ---- DOM helpers --------------------------------------------------------

    private Document parse(String fileName) {
        final String resource = RESOURCE_DIR + fileName;
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("mapping resource not on classpath: " + resource
                    + " (is com.imsweb:x12-parser on the classpath?)");
            }
            final DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setExpandEntityReferences(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            final DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(in);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + resource, e);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse " + resource + ": " + e.getMessage(), e);
        }
    }

    /** Attribute value if set, else the text of the first direct child element of that name, else null. */
    static String attrOrChild(Element el, String name) {
        if (el.hasAttribute(name)) {
            return el.getAttribute(name).trim();
        }
        final Element c = firstChild(el, name);
        return c == null ? null : c.getTextContent().trim();
    }

    static Element firstChild(Element el, String tag) {
        for (Element c : elementChildren(el)) {
            if (tag.equals(c.getTagName())) {
                return c;
            }
        }
        return null;
    }

    static List<Element> children(Element el, String tag) {
        final List<Element> out = new ArrayList<>();
        for (Element c : elementChildren(el)) {
            if (tag.equals(c.getTagName())) {
                out.add(c);
            }
        }
        return out;
    }

    static List<Element> elementChildren(Element el) {
        final List<Element> out = new ArrayList<>();
        final NodeList nl = el.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            final Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                out.add((Element) n);
            }
        }
        return out;
    }

    private static Integer intOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
