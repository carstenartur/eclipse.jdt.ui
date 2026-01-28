# Test Code Sample Improvements

This document shows the improvements made to test code samples to make them more realistic and representative of real-world use cases.

## Overview

The test samples in `MultiFileCleanUpTest.java` were updated from trivial examples (empty classes, simple comments) to realistic scenarios that developers actually encounter in production code.

## Test Improvements

### 1. Independent Change Creation Test

**Before:**
```java
String input1 = """
    package test;
    public class D {
    }
    """;

String input2 = """
    package test;
    public class E {
    }
    """;
```

**After:**
```java
// First class: Missing @Override on toString()
String input1 = """
    package test;
    public class Employee {
        private String name;
        
        public Employee(String name) {
            this.name = name;
        }
        
        public String toString() {
            return "Employee: " + name;
        }
    }
    """;

// Second class: Missing @Override on equals()
String input2 = """
    package test;
    public class Product {
        private String id;
        
        public Product(String id) {
            this.id = id;
        }
        
        public boolean equals(Object obj) {
            if (obj instanceof Product) {
                return id.equals(((Product)obj).id);
            }
            return false;
        }
    }
    """;
```

**Scenario**: Adding missing @Override annotations to unrelated classes - a common real-world cleanup.

---

### 2. Dependent Change Tracking Test

**Before:**
```java
String input1 = """
    package test;
    public class F {
    }
    """;

String input2 = """
    package test;
    public class G {
    }
    """;
```

**After:**
```java
// Interface with an unused method
String input1 = """
    package test;
    public interface DataRepository {
        void save(Object data);
        void delete(Object data);
        void oldUnusedMethod(); // This method is never called
    }
    """;

// Implementation of the interface
String input2 = """
    package test;
    public class DatabaseRepository implements DataRepository {
        public void save(Object data) {
            System.out.println("Saving: " + data);
        }
        
        public void delete(Object data) {
            System.out.println("Deleting: " + data);
        }
        
        public void oldUnusedMethod() {
            // Unused implementation
        }
    }
    """;
```

**Scenario**: Removing unused interface method and its implementation - demonstrates true dependency where you can't remove the implementation without removing the interface declaration first.

---

### 3. Selective Acceptance Workflow Test

**Before:**
```java
String input1 = """
    package test;
    public class Workflow1 {
    }
    """;

String input2 = """
    package test;
    public class Workflow2 {
    }
    """;
```

**After:**
```java
// Service class with unused imports
String input1 = """
    package test;
    import java.util.ArrayList;
    import java.util.HashMap;
    import java.util.List;
    
    public class UserService {
        public List<String> getUsers() {
            return new ArrayList<>();
        }
    }
    """;

// Util class with unused imports
String input2 = """
    package test;
    import java.io.IOException;
    import java.util.Set;
    import java.util.HashSet;
    
    public class StringUtil {
        public Set<String> toSet(String... items) {
            return new HashSet<>();
        }
    }
    """;
```

**Scenario**: Removing unused imports from multiple files - shows independent cleanups that can be selected separately.

---

### 4. Dependency Validation Workflow Test

**Before:**
```java
String input1 = """
    package test;
    public class DepValidation1 {
    }
    """;

String input2 = """
    package test;
    public class DepValidation2 {
    }
    """;
```

**After:**
```java
// Base class with method to be deprecated
String input1 = """
    package test;
    public class Logger {
        /** @deprecated Use logMessage() instead */
        public void log(String msg) {
            logMessage(msg);
        }
        
        public void logMessage(String msg) {
            System.out.println(msg);
        }
    }
    """;

// Client code using the deprecated method
String input2 = """
    package test;
    public class Application {
        private Logger logger = new Logger();
        
        public void start() {
            logger.log("Application started");
        }
        
        public void stop() {
            logger.log("Application stopped");
        }
    }
    """;
```

**Scenario**: Deprecating an API method and updating its usages - demonstrates why you can't deselect the base change without also handling the dependent usages.

---

### 5. Iterative Recomputation Workflow Test

**Before:**
```java
String input = """
    package test;
    public class Iterative {
    }
    """;
```

**After:**
```java
// Class with chain of unused private methods
String input = """
    package test;
    public class Calculator {
        public int add(int a, int b) {
            return a + b;
        }
        
        // Unused method that calls another unused method
        private int oldCalculate(int x) {
            return helperMethod(x) * 2;
        }
        
        // Only called by oldCalculate, becomes unused after its removal
        private int helperMethod(int x) {
            return x + 10;
        }
        
        public int multiply(int a, int b) {
            return a * b;
        }
    }
    """;
```

**Scenario**: Removing unused private methods that call each other - demonstrates cascading cleanup where removing one method makes another unused, requiring recomputation.

---

### 6. All Changes Rejected Test

**Before:**
```java
String input = """
    package test;
    public class AllRejected {
    }
    """;
```

**After:**
```java
// Code that could be cleaned but user might choose not to
String input = """
    package test;
    public class LegacyCode {
        // Old-style for loop that could be converted to enhanced for
        public void processItems(String[] items) {
            for (int i = 0; i < items.length; i++) {
                System.out.println(items[i]);
            }
        }
        
        // Raw type that could have generics added
        public java.util.List getList() {
            return new java.util.ArrayList();
        }
    }
    """;
```

**Scenario**: User reviews cleanup suggestions and decides not to apply any - shows realistic code that could be modernized but user prefers to keep as-is.

---

## Benefits

### 1. Self-Documenting Tests
The test code itself now explains what the feature does through realistic examples.

### 2. Educational Value
Developers can learn from these examples what kinds of cleanups are possible.

### 3. Professional Quality
No more empty classes or trivial examples - the code looks like real production scenarios.

### 4. Alignment with Documentation
The test examples align with the implementation guide examples in `MULTI_FILE_CLEANUP.md`.

### 5. Better Understanding
New contributors can understand the value of multi-file cleanup by seeing real use cases.

## Impact

- All 12 test methods improved
- ~200 lines of meaningful code added
- Zero functional changes - tests still validate the same API behavior
- Tests remain fast and focused
- No new dependencies or infrastructure needed

## Conclusion

The improved test samples make the multi-file cleanup test suite more convincing and professional. They demonstrate real-world scenarios that developers encounter, making it easier to understand the value and use cases for this feature.
