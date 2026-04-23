# Java tutorial for beginners

This guide teaches **core Java** so you can read tutorials and production code (including Spring Boot apps) with confidence. It assumes you can use a terminal and a text editor; it does **not** assume prior Java experience.

**Version note:** Examples use syntax available in **Java 17+** (this repository’s app targets **Java 21**). Older tutorials may omit **records**, **switch expressions**, and **text blocks**; those features appear here because they are common in modern codebases.

**How to use this doc:** Read in order the first time. Later, use the headings as a reference. Type the small programs yourself; muscle memory matters.

---

## Part A — Java in context

### What Java is

Java is a **compiled, statically typed** language: the compiler checks types before your program runs, and source files (`.java`) are compiled to **bytecode** (`.class` files) that run on the **Java Virtual Machine (JVM)**. The JVM is what makes “write once, run anywhere” practical: the same bytecode runs on macOS, Linux, and Windows as long as a JVM exists for that platform.

### JDK, JRE, and JVM

| Term | Meaning |
|------|---------|
| **JVM** | Runs bytecode. |
| **JRE** | JVM + standard libraries (less common as a separate install today). |
| **JDK** | JRE + compiler (`javac`), debugger, standard tools. **Install a JDK** to develop Java. |

For this repo’s Spring Boot app, use a **JDK 21** (or newer LTS when you adopt it). Verify in a terminal:

```bash
java -version
javac -version
```

Both commands should report version **21** (or the JDK you intentionally installed).

### Source file and class name

A **public** class must live in a file named **exactly** `ClassName.java`. One public top-level class per file is the usual rule.

```java
public class Hello {
  public static void main(String[] args) {
    System.out.println("Hello, Java");
  }
}
```

Compile and run from the directory that contains `Hello.java`:

```bash
javac Hello.java
java Hello
```

`main` is the **entry point**: the JVM calls `public static void main(String[] args)` when you run `java Hello`.

### Packages and directories

Packages are **namespace** segments separated by dots. They map to **folders**.

If the first line of a file is `package com.example.demo;`, the file must sit under `com/example/demo/` (relative to a **source root**). Tools like Gradle use `src/main/java` as that root.

```java
package com.example.demo;

public class App {
  public static void main(String[] args) {
    System.out.println("Packaged app");
  }
}
```

Run with the **fully qualified class name** (package + class):

```bash
java com.example.demo.App
```

The JVM needs the **classpath**: roots where it looks for compiled classes. From `src/main/java`, after compiling, classpath typically includes `build/classes/java/main` (Gradle) or a folder you pass with `java -cp ...`.

---

## Part B — Language fundamentals

### Comments and code blocks

```java
// End-of-line comment

/*
 * Block comment (can span lines)
 */

/**
 * Javadoc: used to document public APIs. Tools can generate HTML from this.
 */
```

Statements often end with **`;`**. Code is grouped in **`{ ... }` blocks**.

### Primitive types

These store **values** directly (not object headers on the heap, except when boxed).

| Type | Approximate role | Example literal |
|------|------------------|-----------------|
| `byte` | Tiny integer | `(byte) 1` |
| `short` | Small integer | `(short) 1000` |
| `int` | Default integer | `42` |
| `long` | Wider integer | `42L` |
| `float` | 32-bit floating point | `3.14f` |
| `double` | 64-bit floating point | `3.14` |
| `char` | Single UTF-16 code unit | `'A'` |
| `boolean` | true/false | `true` |

**Numeric literals:** Underscores improve readability: `1_000_000`.

**Casting:** Narrowing conversions can lose data; the compiler may require an explicit cast: `int x = (int) someLong;`.

### Reference types and `null`

**Reference types** (classes, interfaces, arrays) store a **reference** (like a pointer) to an object or array. The special literal **`null`** means “references nothing.” Calling a method on `null` throws **`NullPointerException`**.

```java
String name = null;
// name.length(); // throws NullPointerException at runtime
```

Modern APIs often use **`Optional`** (see later) to avoid surprising `null`s.

### Variables with `var` (local inference)

Inside methods, you can write **`var`** instead of repeating the type when the compiler can infer it:

```java
var count = 10;        // inferred as int
var label = "hello";   // inferred as String
```

`var` is **not** dynamic typing; the type is fixed at compile time. It does **not** replace types on fields or method parameters (except in lambdas where types are often omitted anyway).

### Operators (essentials)

- Arithmetic: `+ - * / %`
- Comparison: `== != < > <= >=`
- Logical: `&& || !` (short-circuiting: `&&` and `||` skip evaluating the right side when the result is already known)
- Assignment: `= += -= ...`
- Ternary: `condition ? whenTrue : whenFalse`

**Pitfall:** `==` on **reference types** compares **references**, not object contents. For strings, prefer **`equals`**:

```java
String a = new String("hi");
String b = new String("hi");
System.out.println(a == b);      // often false (different objects)
System.out.println(a.equals(b)); // true (same characters)
```

### `String` and text blocks

`String` is **immutable**: methods like `toUpperCase()` return a **new** string.

Useful methods include `length()`, `isEmpty()`, `charAt(i)`, `substring(...)`, `strip()`, `startsWith`, `contains`, `split`, and `formatted` / `String.format` for templating.

**Text block** (Java 15+): multi-line string with less escaping:

```java
String yamlLike = """
    line1: a
    line2: b
    """;
```

Trailing spaces and indentation rules are defined in the language spec; for tutorials, keep content visually left-aligned inside the block until you are comfortable with the rules.

### Control flow: `if` / `else`

```java
int score = 85;
if (score >= 90) {
  System.out.println("A");
} else if (score >= 80) {
  System.out.println("B");
} else {
  System.out.println("C or below");
}
```

### `switch`: statements and expressions

**Classic switch statement** still exists. Prefer **switch expressions** (Java 14+) when each branch produces a value:

```java
String day = "MON";
String kind = switch (day) {
  case "SAT", "SUN" -> "weekend";
  case "MON", "TUE", "WED", "THU", "FRI" -> "weekday";
  default -> "unknown";
};
```

Exhaustiveness: switching on an **enum** can be checked so you do not forget a constant.

### Loops

**`for`** with three parts:

```java
for (int i = 0; i < 5; i++) {
  System.out.println(i);
}
```

**Enhanced `for`** (for-each) over arrays and anything `Iterable`:

```java
int[] nums = {1, 2, 3};
for (int n : nums) {
  System.out.println(n);
}
```

**`while` / `do-while`** when the iteration count is not known up front.

**`break` and `continue`:** exit loop or skip to next iteration.

### Arrays

Fixed size after creation:

```java
int[] a = new int[3];      // {0,0,0}
int[] b = {10, 20, 30};    // initializer
System.out.println(b.length);
```

Multi-dimensional arrays are **arrays of arrays**: `int[][] grid = new int[3][4];`

---

## Part C — Object-oriented programming

### Class, object, field, method

A **class** is a blueprint; an **object** is an instance at runtime.

```java
public class Counter {
  private int value = 0; // field (instance variable)

  public void increment() { // instance method
    value++;
  }

  public int current() {
    return value;
  }
}
```

Use it:

```java
Counter c = new Counter();
c.increment();
System.out.println(c.current());
```

### Constructors

Constructors initialize new objects. The name matches the class; there is no return type.

```java
public class Point {
  private final int x;
  private final int y;

  public Point(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public int x() { return x; }
  public int y() { return y; }
}
```

If you declare **no** constructors, Java provides a **default no-arg** constructor. If you declare any constructor, the default goes away unless you add one yourself.

### `this`

`this` refers to the **current object**. Use it to disambiguate parameters from fields (`this.x = x`) or to pass “this instance” to another method.

### `static`

**`static` members belong to the class**, not to a particular instance.

```java
public class Mathy {
  public static int add(int a, int b) {
    return a + b;
  }
}

int sum = Mathy.add(2, 3);
```

`main` is `static` because the JVM calls it **before** any object of your class necessarily exists.

### Access modifiers

| Modifier | Class | Package | Subclass | World |
|----------|-------|---------|----------|-------|
| `public` | yes | yes | yes | yes |
| `protected` | yes | yes | yes | no |
| (package-private / default) | yes | yes | no | no |
| `private` | yes | no | no | no |

**Encapsulation:** keep fields `private`; expose behavior via methods so you can change internals safely.

### Inheritance (`extends`)

A subclass **inherits** fields and methods of a superclass (respecting visibility).

```java
public class Animal {
  public String speak() {
    return "...";
  }
}

public class Dog extends Animal {
  @Override
  public String speak() {
    return "woof";
  }
}
```

**`@Override`** is an annotation that catches typos at compile time: you meant to override a superclass method but misspelled the name.

**Single inheritance:** a class extends **at most one** class. Use **interfaces** for additional contracts.

### `super`

Call a superclass constructor: `super(args);` must be the **first** line of a subclass constructor if present.

Call a superclass method: `super.methodName();`

### `abstract` classes and methods

An **abstract class** cannot be instantiated. It may declare **abstract methods** (no body) that subclasses must implement.

```java
public abstract class Shape {
  public abstract double area();
}
```

Use abstract classes when subclasses share real implementation; use interfaces when you mainly define **capability** across unrelated types.

### Interfaces (`implements`)

An **interface** declares **abstract** instance methods (since Java 8, it may also declare **`default`** methods with bodies and **`static`** methods).

```java
public interface Named {
  String name();

  default String greeting() {
    return "Hello, " + name();
  }
}
```

A class **`implements`** one or more interfaces.

### Polymorphism

A variable of type **`Animal`** can refer to a **`Dog`** instance. Calling `speak()` **dispatches** to `Dog`’s implementation at runtime (for instance methods; `static` methods do not participate in this dispatch).

```java
Animal a = new Dog();
System.out.println(a.speak()); // woof
```

### `final`

- **`final` class:** cannot be subclassed.
- **`final` method:** cannot be overridden.
- **`final` variable:** assigned once (for fields, must be assigned by the end of every constructor if not at declaration).

### Enumerations (`enum`)

Fixed set of named constants with optional fields and methods:

```java
public enum Season {
  SPRING,
  SUMMER,
  FALL,
  WINTER
}
```

Enums are full classes: they can have constructors (private by convention), fields, and methods. They work well in **`switch`** with exhaustiveness checking.

---

## Part D — Practical everyday Java

### Generics (basics)

**Generics** let you parameterize types: `List<String>` is a list that **should** only hold strings; the compiler enforces that (modulo legacy raw types, which you should avoid).

```java
import java.util.ArrayList;
import java.util.List;

List<String> names = new ArrayList<>();
names.add("Ada");
// names.add(123); // compile error
```

Common type parameters: **`E`** element, **`K`** key, **`V`** value, **`T`** type.

### Collections overview

In **`java.util`**, common types include:

| Interface | Typical implementations | Notes |
|-----------|-------------------------|-------|
| `List` | `ArrayList`, `LinkedList` | Ordered, allows duplicates |
| `Set` | `HashSet`, `TreeSet` | No duplicates |
| `Map` | `HashMap`, `TreeMap` | Key → value |

Iterate a `Map`:

```java
import java.util.Map;

for (Map.Entry<String, Integer> e : map.entrySet()) {
  System.out.println(e.getKey() + " -> " + e.getValue());
}
```

### Exceptions

**Checked exceptions** (subclasses of `Exception` but not `RuntimeException`) must be **declared** (`throws`) or **handled** (`try/catch`) at compile time.

**Unchecked exceptions** (`RuntimeException` and subclasses, e.g. `IllegalArgumentException`, `NullPointerException`) do not require `throws` on every caller.

```java
public int parsePositiveInt(String s) {
  int value = Integer.parseInt(s); // may throw NumberFormatException (unchecked)
  if (value <= 0) {
    throw new IllegalArgumentException("must be positive");
  }
  return value;
}
```

**`try-with-resources`** closes `AutoCloseable` resources automatically:

```java
import java.io.BufferedReader;
import java.io.StringReader;

try (BufferedReader br = new BufferedReader(new StringReader("line"))) {
  System.out.println(br.readLine());
}
```

### `Optional<T>`

`Optional` is a **box** that may or may not contain a value. Use it for **return types** where “missing” is normal; avoid using `Optional` for fields in most code.

```java
import java.util.Optional;

public Optional<String> findNickname(String id) {
  if ("1".equals(id)) {
    return Optional.of("Ace");
  }
  return Optional.empty();
}

findNickname("1").ifPresent(System.out::println);
String forced = findNickname("x").orElse("unknown");
```

### `record` (immutable data carriers)

A **record** declares **state** (`components`) and generates a constructor, accessors (`name()` for component `name`), `equals`, `hashCode`, and `toString`.

```java
public record Person(String name, int age) {}
```

Usage:

```java
Person p = new Person("Lee", 30);
System.out.println(p.name());
```

Records fit **DTOs** (data transfer objects) in REST APIs: JSON maps cleanly to components. You will see many `record` types in modern Spring code.

You can add **compact constructors** for validation:

```java
public record PositiveInt(int value) {
  public PositiveInt {
    if (value <= 0) {
      throw new IllegalArgumentException("value must be positive");
    }
  }
}
```

### Lambdas and functional interfaces

A **functional interface** has **exactly one abstract method** (e.g. `Runnable`, `Comparator<T>`, `java.util.function.Predicate<T>`).

**Lambda syntax:** parameters → body.

```java
import java.util.function.Predicate;

Predicate<String> nonBlank = s -> !s.isBlank();
System.out.println(nonBlank.test("hi")); // true
```

**Method references:**

```java
names.forEach(System.out::println);
```

### Streams (introductory)

**Streams** process sequences in a fluent style: source → intermediate operations → terminal operation.

```java
import java.util.List;

List<String> names = List.of("ann", "bob", "cara");

long longNames =
    names.stream()
        .filter(s -> s.length() > 3)
        .map(String::toUpperCase)
        .peek(System.out::println)
        .count();
```

Streams can be **lazy**: nothing runs until a **terminal** operation (`collect`, `count`, `findFirst`, …). For **parallel** streams, understand thread safety before using `parallelStream()`.

### `java.time` (dates and times)

Prefer **`java.time`** over legacy `Date`/`Calendar`.

```java
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

LocalDate today = LocalDate.now();
Instant nowUtc = Instant.now();
ZonedDateTime here = ZonedDateTime.now(ZoneId.of("America/Los_Angeles"));
```

REST APIs and databases often serialize instants in **ISO-8601** strings; parsing/building uses `DateTimeFormatter` when you need custom formats.

### Annotations (what they are)

**Annotations** attach metadata to declarations (`@Override`, `@Deprecated`) or sites in code. Frameworks like Spring read them at **compile time** (some processors) or **runtime** (reflection) to wire behavior.

You will not implement your own framework on day one; you **consume** annotations. Knowing they exist explains “magic” in Spring Boot.

---

## Part E — How pieces fit a real project

### Build tools: Gradle or Maven

Real Java apps are not one `javac` file. **Gradle** (this repo’s `my-app/`) or **Maven** declares dependencies, compiles tests, and packages a JAR. After you are comfortable with Java syntax, open `my-app/build.gradle` and skim **plugins**, **Java version**, and **dependencies**.

### Testing with JUnit 5 (conceptual)

Tests are methods annotated (for example) with **`@Test`**. Assertions check expected outcomes. Spring projects often add **`@SpringBootTest`** for integration tests; pure unit tests need **no** framework beyond JUnit and maybe Mockito.

### Reading this repository after this tutorial

Suggested path:

1. Finish this document and write a few tiny `main` programs (records, lists, exceptions).
2. Read **[spring-boot-tutorial.md](spring-boot-tutorial.md)** for Spring Boot (written as a follow-on to this document).
3. Read **[backend-concepts.md](backend-concepts.md)** in this folder to see how those concepts map to **`my-app/`** source files.

Product-facing behavior (APIs, environment variables) lives under **`doc/`** at the repository root.

---

## Quick reference cheat sheet

| I want to… | Use… |
|------------|------|
| Store a whole number | `int` / `long` |
| Store text | `String` |
| Group related data immutably | `record` |
| Growable list | `ArrayList` |
| Key/value map | `HashMap` |
| Avoid null returns | `Optional` |
| Transform a collection fluently | Stream API |
| Represent a calendar date | `LocalDate` |
| Represent a moment in UTC | `Instant` |
| Signal failure | throw `IllegalArgumentException` or a domain-specific type |
| Run the program | `public static void main(String[] args)` |

---

## Appendix — Common compiler errors (beginner)

| Message (simplified) | Typical cause |
|----------------------|----------------|
| cannot find symbol | Typo, wrong import, or missing dependency on classpath |
| incompatible types | Assigning a `String` to an `int`, etc. |
| variable might not have been initialized | Local variable used before definite assignment |
| unreported exception … must be caught or declared | Checked exception not handled |
| class X is public, should be declared in a file named X.java | File name mismatch |

When stuck, read the **first** error in the compiler output; later errors are often cascades from the first.

---

If you want exercises next (small programs with solutions), say so and they can be added as a follow-up section or companion file.
