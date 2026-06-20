# SwiggyX — Maven Commands Reference

### Every Maven command we used, and exactly when to use each one.

---

## Prerequisites — One Time Setup

### Verify Java and Maven versions

```bash
java -version
mvn -version
```

Both should show Java 17.

If Maven shows a different Java version (like 25) — see "Fixing Java Version Mismatch" below.

---

## The Core Commands — Daily Use

### 1. Run the application

```bash
mvn spring-boot:run
```

**When to use:** Every time you want to start the SwiggyX server.

**What it does:**

```
Compiles code (if changed)
Starts embedded Tomcat on port 8080
Connects to PostgreSQL
Initializes Spring Boot context
Server stays running until you stop it (Ctrl+C)
```

---

### 2. Clean + Run (most commonly used)

```bash
mvn clean spring-boot:run
```

**When to use:** After making ANY code changes. This is your go-to command.

**What it does:**

```
clean           → deletes old compiled .class files in /target
spring-boot:run → recompiles everything fresh, then runs

Why clean matters:
Sometimes old compiled classes cause weird bugs
clean ensures you're running the LATEST code
Slightly slower than plain spring-boot:run, but safer
```

**Use this 95% of the time during development.**

---

### 3. Just compile (no run)

```bash
mvn compile
```

**When to use:** Quick check if code compiles without errors, without starting the server.

**What it does:**

```
Compiles all .java files to .class files
Does NOT start the server
Fast way to catch syntax errors
```

---

### 4. Clean + Compile (no run)

```bash
mvn clean compile
```

**When to use:** We used this to fix the "Cannot resolve method 'builder'" Lombok issue.

**What it does:**

```
Deletes old compiled classes
Recompiles fresh
Runs annotation processors (Lombok) fresh
Does NOT start server — just verifies compilation works
```

**Good for:** Verifying Lombok annotations (@Builder, @Data) are being processed correctly before running the full app.

---

### 5. Resolve dependencies

```bash
mvn dependency:resolve
```

**When to use:** After changing `pom.xml` — to verify all dependencies download correctly.

**What it does:**

```
Downloads all dependencies listed in pom.xml
Does NOT compile or run anything
Just verifies dependencies are available
Look for "BUILD SUCCESS" at the end
```

**We used this right after setting up pom.xml the first time.**

---

## Troubleshooting Commands

### Force Java 17 for a single command (temporary fix)

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home && mvn clean spring-boot:run
```

**When to use:** If `mvn -version` shows wrong Java version AND you haven't fixed it permanently yet.

**What it does:**

```
export JAVA_HOME=...  → sets Java version for THIS terminal session only
&& mvn clean spring-boot:run → runs with that Java version

Limitation: Only works for this one command/session.
New terminal = forgets this setting.
```

**We needed this before fixing jenv permanently.**

---

## One-Time Permanent Fixes

### Fix 1 — jenv Maven plugin (what actually fixed our Java version issue)

```bash
jenv enable-plugin maven
source ~/.zshrc
```

**When to use:** ONCE, if Maven keeps using the wrong Java version even after setting JAVA_HOME.

**Why this was needed:**

```
We had jenv (Java version manager) installed
jenv was overriding our manual JAVA_HOME export
jenv enable-plugin maven tells jenv to control Maven's Java version too
After this — mvn -version permanently shows correct Java version
```

**Verify it worked:**

```bash
jenv versions
mvn -version
```

---

### Fix 2 — .zshrc permanent JAVA_HOME (backup/parallel fix)

```bash
open ~/.zshrc
```

Add these lines at the bottom:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
```

Then:

```bash
source ~/.zshrc
```

**When to use:** ONCE, as part of initial machine setup. Works alongside jenv fix.

---

## Decision Tree — Which Command to Use When

```
Just changed code, want to test it?
→ mvn clean spring-boot:run

Want to quickly check for compile errors only?
→ mvn clean compile

Just changed pom.xml, want to verify dependencies download?
→ mvn dependency:resolve

Server already running, just testing endpoints?
→ no Maven command needed — just use curl/Postman

Getting weird errors that don't make sense?
→ mvn clean spring-boot:run (clean fixes most weird issues)

Maven using wrong Java version?
→ Check: mvn -version
→ If wrong: jenv enable-plugin maven && source ~/.zshrc
→ Verify: mvn -version again
```

---

## Our Actual Debugging Journey (For Reference)

This is the exact sequence we went through — useful if you hit similar issues:

```
1. mvn dependency:resolve
   → BUILD SUCCESS (dependencies downloaded fine)

2. mvn spring-boot:run
   → ERROR: Unsupported class file major version 68
   → Problem: Maven using Java 25, Spring Boot needs Java 17/21

3. Checked: java -version
   → Showed Java 17 (correct)

4. Checked: mvn -version
   → Showed Java 25 (wrong! Maven using different Java than java command)

5. Checked: /usr/libexec/java_home -V
   → Confirmed Java 17 was installed correctly

6. Temporary fix: export JAVA_HOME=... && mvn spring-boot:run
   → Worked for that session only

7. Found root cause: jenv was overriding JAVA_HOME
   → jenv versions showed Java 17 was set as default
   → But jenv wasn't controlling Maven specifically

8. Permanent fix: jenv enable-plugin maven
   → source ~/.zshrc
   → mvn -version now permanently shows Java 17

9. mvn clean spring-boot:run
   → BUILD FAILURE: Failed to configure DataSource
   → Problem: hadn't created application.properties yet

10. Created application.properties with DB connection details

11. mvn clean spring-boot:run
    → SUCCESS! Server started on port 8080
```

---

## Quick Command Cheat Sheet

```bash
# Daily development
mvn clean spring-boot:run        # most common — run after any code change

# Quick checks
mvn clean compile                # verify compilation only
mvn dependency:resolve           # verify pom.xml dependencies

# One-time setup (already done for this project)
jenv enable-plugin maven
source ~/.zshrc

# Verify environment
java -version
mvn -version
```

---

*Last updated: Complete Maven command reference with troubleshooting journey*