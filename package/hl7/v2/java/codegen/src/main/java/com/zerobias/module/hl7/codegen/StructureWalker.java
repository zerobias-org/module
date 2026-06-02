package com.zerobias.module.hl7.codegen;

import ca.uhn.hl7v2.model.AbstractSegment;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.primitive.ID;
import ca.uhn.hl7v2.model.primitive.IS;
import com.zerobias.module.hl7.codegen.model.Property;
import com.zerobias.module.hl7.codegen.model.Reference;
import com.zerobias.module.hl7.codegen.model.Schema;
import com.zerobias.module.hl7.codegen.model.StructureIndex;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Walks HAPI's typed structure classes for one HL7 version and accumulates
 * DataProducer schemas (DESIGN §2.3) plus the materializer structure-index
 * (DESIGN §5).
 *
 * <p>Composition all the way down: a message references its segments/groups, a
 * segment references its composite datatypes, a composite references its nested
 * composites; primitives map to core dataTypes (DESIGN §2.4) and table-bound
 * primitives (ID/IS) additionally carry a {@code schema:enum:} reference.
 *
 * <p>Traversal reuses the {@link Type} instances HAPI constructs for each field
 * (via {@code AbstractSegment.getField}), so composites are never manually
 * instantiated. Each distinct structure class is emitted once (deduped by simple
 * name).
 */
public final class StructureWalker {

    private final String version;

    // Emitted schemas, keyed by structure simple name.
    final Map<String, Schema> messages = new LinkedHashMap<>();
    final Map<String, Schema> groups = new LinkedHashMap<>();
    final Map<String, Schema> segments = new LinkedHashMap<>();
    final Map<String, Schema> datatypes = new LinkedHashMap<>();
    final Set<Integer> tables = new TreeSet<>();
    final StructureIndex index;

    public StructureWalker(String version) {
        this.version = version;
        this.index = new StructureIndex(version);
    }

    /** Walk one message structure (e.g. {@code ADT_A01}) and everything it references. */
    public void walkMessage(String messageSimpleName) throws Exception {
        if (messages.containsKey(messageSimpleName)) {
            return;
        }
        final Message message = instantiateMessage(messageSimpleName);
        final Schema schema = new Schema(SchemaIds.requireValid(SchemaIds.table(version, messageSimpleName)));
        final StructureIndex.MessageEntry entry = new StructureIndex.MessageEntry();
        index.messages.put(messageSimpleName, entry);
        messages.put(messageSimpleName, schema); // reserve before recursion (cycle-safety)
        walkChildren(message, schema, entry);
    }

    private void walkGroupType(Group group, String simpleName) throws Exception {
        if (groups.containsKey(simpleName)) {
            return;
        }
        final Schema schema = new Schema(SchemaIds.requireValid(SchemaIds.type(version, simpleName)));
        final StructureIndex.MessageEntry entry = new StructureIndex.MessageEntry();
        index.groups.put(simpleName, entry);
        groups.put(simpleName, schema);
        walkChildren(group, schema, entry);
    }

    /** Shared child iteration for a message or nested group. */
    private void walkChildren(Group group, Schema schema, StructureIndex.MessageEntry entry) throws Exception {
        for (String name : group.getNames()) {
            final boolean required = group.isRequired(name);
            final boolean repeating = group.isRepeating(name);
            final boolean isGroup = group.isGroup(name);
            final Structure child = group.get(name); // instantiate rep 0
            final String structSimple = child.getClass().getSimpleName();
            final String prop = name.toLowerCase(Locale.ROOT);

            schema.properties.add(new Property(prop, CoreTypes.STRING)
                .required(required)
                .multi(repeating)
                .references(new Reference(SchemaIds.type(version, structSimple))));
            entry.structures.add(new StructureIndex.StructureRef(prop, structSimple, isGroup, required, repeating));

            if (isGroup) {
                walkGroupType((Group) child, structSimple);
            } else {
                walkSegment((Segment) child, structSimple);
            }
        }
    }

    private void walkSegment(Segment segment, String simpleName) throws Exception {
        if (segments.containsKey(simpleName)) {
            return;
        }
        final Schema schema = new Schema(SchemaIds.requireValid(SchemaIds.type(version, simpleName)));
        final StructureIndex.SegmentEntry seg = new StructureIndex.SegmentEntry();
        index.segments.put(simpleName, seg);
        segments.put(simpleName, schema);

        final AbstractSegment as = (AbstractSegment) segment;
        final Map<Integer, String> beanNames = positionalBeanNames(segment.getClass());
        final String[] fieldNames = as.getNames();
        final int n = as.numFields();
        for (int i = 1; i <= n; i++) {
            final int fieldNum = i; // effectively-final capture for the lambda below
            final String bean = beanNames.getOrDefault(i, HapiNames.fromDescription(fieldNames[i - 1]));
            final boolean required = as.isRequired(i);
            final int maxCard = as.getMaxCardinality(i);
            final boolean multi = maxCard != 1; // -1 (unbounded) or >1
            final Type field = as.getField(i, 0);
            addTypedProperty(schema, field, bean, required, multi,
                (type, table) -> seg.fields.add(
                    new StructureIndex.FieldEntry(fieldNum, bean, type, multi, required, table)));
        }
    }

    private void walkDatatype(Composite composite, String simpleName) throws Exception {
        if (datatypes.containsKey(simpleName)) {
            return;
        }
        final Schema schema = new Schema(SchemaIds.requireValid(SchemaIds.type(version, simpleName)));
        final StructureIndex.DatatypeEntry dt = new StructureIndex.DatatypeEntry();
        index.datatypes.put(simpleName, dt);
        datatypes.put(simpleName, schema);

        final Type[] components = composite.getComponents();
        final Map<Integer, String> beanNames = positionalBeanNames(composite.getClass());
        for (int i = 0; i < components.length; i++) {
            final int idx = i + 1;
            final String bean = beanNames.getOrDefault(idx, "component" + idx);
            // Components carry no required/multi at the composite level.
            addTypedProperty(schema, components[i], bean, false, false,
                (type, table) -> dt.components.add(
                    new StructureIndex.ComponentEntry(idx, bean, type, table)));
        }
    }

    /** Sink for a resolved field/component's index entry: (hl7TypeSimpleName, tableOrNull). */
    @FunctionalInterface
    private interface IndexSink {
        void accept(String hl7TypeSimpleName, Integer table);
    }

    /**
     * Add one property to a schema based on the runtime type of an HL7
     * field/component, recursing into composites. Primitive → core dataType
     * (+ enum reference for ID/IS); composite → composition reference + recurse;
     * Varies/unknown → string.
     */
    private void addTypedProperty(Schema schema, Type type, String bean,
                                  boolean required, boolean multi, IndexSink sink) throws Exception {
        final String hl7Type = type.getClass().getSimpleName();

        if (type instanceof Primitive) {
            final String core = CoreTypes.forPrimitive(hl7Type);
            final Property p = new Property(bean, core).required(required).multi(multi);
            Integer table = null;
            if (CoreTypes.isEnumBound(hl7Type)) {
                table = tableOf(type);
                if (table != null && table > 0) {
                    tables.add(table);
                    p.references(new Reference(SchemaIds.enumTable(version, table)));
                } else {
                    table = null;
                }
            }
            final String fmt = CoreTypes.formatHint(hl7Type);
            if (fmt != null) {
                p.format(fmt);
            }
            schema.properties.add(p);
            sink.accept(hl7Type, table);
        } else if (type instanceof Composite) {
            schema.properties.add(new Property(bean, CoreTypes.STRING)
                .required(required)
                .multi(multi)
                .references(new Reference(SchemaIds.type(version, hl7Type))));
            sink.accept(hl7Type, null);
            walkDatatype((Composite) type, hl7Type);
        } else {
            // Varies / unknown leaf — represent as opaque string.
            schema.properties.add(new Property(bean, CoreTypes.STRING).required(required).multi(multi));
            sink.accept(hl7Type, null);
        }
    }

    private static Integer tableOf(Type type) {
        if (type instanceof ID) {
            return ((ID) type).getTable();
        }
        if (type instanceof IS) {
            return ((IS) type).getTable();
        }
        return null;
    }

    /**
     * Build {fieldNumber → beanName} from HAPI's generated positional accessors
     * (e.g. {@code getPid3_PatientIdentifierList}). These encode the authoritative
     * 1-based index and the bean name a HAPI client sees (DESIGN §2.3).
     *
     * <p>Only accessors that return an HL7 {@link Type} (or {@code Type[]}) are
     * considered. HAPI also generates a {@code getPidN_FieldReps()} method that
     * returns the repetition <em>count</em> ({@code int}) and matches the same
     * name pattern; filtering by return type excludes it (otherwise repeating
     * fields would nondeterministically pick up a {@code ...Reps} bean name,
     * depending on reflection method order).
     */
    private static Map<Integer, String> positionalBeanNames(Class<?> structureClass) {
        final Map<Integer, String> map = new HashMap<>();
        for (Method m : structureClass.getMethods()) {
            if (!returnsHl7Type(m)) {
                continue;
            }
            HapiNames.parseAccessor(m.getName())
                .ifPresent(a -> map.putIfAbsent(a.index(), a.beanName()));
        }
        return map;
    }

    private static boolean returnsHl7Type(Method m) {
        final Class<?> rt = m.getReturnType();
        if (Type.class.isAssignableFrom(rt)) {
            return true;
        }
        return rt.isArray() && Type.class.isAssignableFrom(rt.getComponentType());
    }

    private Message instantiateMessage(String messageSimpleName) throws Exception {
        final String fqn = "ca.uhn.hl7v2.model." + version + ".message." + messageSimpleName;
        final Class<?> cls = Class.forName(fqn);
        return (Message) cls.getDeclaredConstructor().newInstance();
    }
}
