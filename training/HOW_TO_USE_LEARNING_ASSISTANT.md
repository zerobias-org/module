# 🎓 How to Use Interactive Learning Mode

**Your Personal Learning Companion - Activated by training/ Folder Context**

---

## 🎯 How It Works

When you run Claude Code from the **training/ directory**, Claude automatically becomes your learning companion thanks to the `CLAUDE.md` file.

**No special commands needed!** Just be in the training/ folder.

---

## 🚀 Quick Start

### Step 1: Navigate to Training Folder
```bash
cd /Users/ctamas/code/zborg/module/training/
```

### Step 2: Start Claude Code
Open Claude Code (or your IDE) from this directory.

### Step 3: Just Ask!
```
Help me learn module development!
```

Claude will guide you through the training!

---

## ⚠️ CRITICAL: Training vs Production

### 🎓 Training Folder (LEARNING)
```bash
cd /Users/ctamas/code/zborg/module/training/
```

**Purpose**: Interactive learning, practice, exercises

**You can**:
- ✅ Ask questions about concepts
- ✅ Get quizzes and mindmaps
- ✅ See code examples with explanations
- ✅ Practice exercises
- ✅ Learn at your own pace

**You CANNOT**:
- ❌ Build production modules
- ❌ Use /create-module
- ❌ Use /add-operation
- ❌ Debug actual module issues

---

### 🏗️ Repository Root (PRODUCTION)
```bash
cd /Users/ctamas/code/zborg/module/
```

**Purpose**: Actual module development

**You can**:
- ✅ /create-module vendor service
- ✅ /add-operation module-id operation
- ✅ Build real modules
- ✅ Fix actual bugs

---

## 💬 Example Interactions

### Starting Your Learning Journey

**You** (in training/ folder):
```
I'm new to module development, where do I start?
```

**Claude**:
```
Welcome! 🎉

Since you're new, I recommend the Beginner Complete path.

You'll learn:
- The 8-phase process
- All 6 quality gates
- The 12 critical rules

---
**What would you like to do?**
a) Read path overview
b) Jump to Chapter 1
c) See all 3 paths
d) Ask a question
```

**You**:
```
b
```

**Claude**:
```
[Explains Chapter 1]
```

---

### Getting Real Code Examples

**You**:
```
Show me a real mapper example
```

**Claude**:
```
[Searches package/ directory]
[Finds real mapper]
[Adds inline comments]

Here's a real mapper from the GitHub module:

```typescript
// REAL EXAMPLE FROM: package/github/github/src/Mappers.ts

export function toUser(raw: any): User {
  // Validate all required fields are present in raw API data
  // This throws InvalidStateError if any field is null/undefined
  ensureProperties(raw, ['id', 'email']);

  // Create typed output with explicit type declaration
  const output: User = {
    // Convert numeric ID to string (API returns number, we use string)
    id: raw.id.toString(),

    // Use map() utility to convert string → Email type
    // Automatically returns undefined if raw.email is null
    email: map(Email, raw.email),

    // Optional field - use optional() to normalize null → undefined
    name: optional(raw.name)
  };

  return output;
}
```

---
**What would you like to do?**
a) See another mapper
b) See a Producer example
c) Quiz me on mappers
d) Create a mindmap
```

---

### Taking Quizzes

**You**:
```
Quiz me on the 12 critical rules
```

**Claude**:
```
📝 The 12 Critical Rules Quiz

1. What's the correct operationId for retrieving a user?
   a) describeUser
   b) getUser ✅
   c) fetchUser

2. Which response codes in api.yml?
   a) 200, 404, 500
   b) 200, 201 ✅
   c) All codes

[10 more questions...]

Type your answers (e.g., "1b 2b 3a...")
```

---

## 🎯 What You Can Do

### Learning Navigation
```
Where should I start?
I finished Chapter 6, what's next?
Am I ready for implementation?
```

### Concept Questions
```
What are the 6 quality gates?
Explain mappers in simple terms
Why do we use ensureProperties()?
What's the difference between unit and integration tests?
```

### Request Real Code
```
Show me a real mapper
Show me a real Producer with pagination
Show me how Client handles OAuth
Show me a unit test with nock
Show me integration test debug logging
```

### Get Interactive
```
Quiz me on Chapter 6
Create a mindmap of module architecture
Test my understanding of the 12 rules
Show me a mindmap of the 8-phase process
```

### Track Progress
```
Where am I in my learning?
What should I review before Chapter 7?
Am I ready for exercises?
```

---

## 🎓 Learning Session Flow

### Typical Session

```
1. cd training/
2. Start Claude Code
3. Ask question or request guidance
4. Claude responds with explanation
5. Claude offers letter choices (a, b, c, d)
6. You respond with single letter
7. Claude continues based on your choice
8. Repeat!
```

### Example Full Session

```
You: Help me learn mappers

Claude: [Explains mappers concept]
        ---
        **What would you like to do?**
        a) See real mapper code
        b) Quiz me on mappers
        c) Learn about Producers
        d) Create a mindmap

You: a

Claude: [Shows real code with inline comments]
        ---
        **What would you like to do?**
        a) See another mapper
        b) Quiz me on mappers
        c) Learn about Producers
        d) Create a mindmap

You: b

Claude: [Creates quiz]
        [You take quiz]
        [Claude scores and explains]
        ---
        **What would you like to do?**
        a) Review mappers
        b) Move to Producers
        c) Try an exercise
        d) Create a mindmap

You: b

Claude: [Explains Producers...]
```

---

## 💡 Pro Tips

### 1. Stay in training/ Folder
Keep Claude Code running from training/ directory while learning.

### 2. Use Single Letters
When Claude offers choices, just type: "a" or "b" or "c" or "d"

### 3. Ask for Real Code
Say "Show me a real [component]" to see production code with explanations.

### 4. Take Quizzes
After each chapter, ask: "Quiz me on Chapter X"

### 5. Get Mindmaps
Visual learner? Ask: "Create a mindmap of [topic]"

### 6. Switch to Production When Ready
```bash
# Exit training
cd ..
# Now in repository root - ready for production
/create-module vendor service
```

---

## 🚫 Common Mistakes

### ❌ Trying to Build Production Modules in training/
```
Wrong: (in training/) /create-module github github
```

Claude will remind you:
```
"I'm in learning mode! For production work:
1. Exit training/ folder
2. cd to repository root
3. Use /create-module command

Want to PRACTICE as an exercise instead?"
```

### ❌ Not Using Letter Responses
```
Wrong: "I want to see code examples"
Right: "a"
```

### ❌ Working from Wrong Directory
```
For learning: cd /Users/ctamas/code/zborg/module/training/
For production: cd /Users/ctamas/code/zborg/module/
```

---

## 🎓 Learning Modes Summary

| Location | Mode | Purpose | Commands |
|----------|------|---------|----------|
| training/ | LEARNING | Study, practice, learn | Questions, quizzes, mindmaps |
| repository root | PRODUCTION | Build modules | /create-module, /add-operation |

---

## 🚀 Get Started

```bash
cd /Users/ctamas/code/zborg/module/training/
```

Then just ask:
```
Help me learn module development!
```

**Your interactive learning companion is ready!** 🎓

---

**Key Points**:
- Run from training/ folder for learning mode
- No @buddy needed - automatic with folder context
- Just type letters (a, b, c, d) to navigate
- Exit to root for production work
- Ask for real code - get commented examples!
