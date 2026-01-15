# Build Fix Instructions for this PR

## Problem Summary

The PR build is failing due to a **Java version compatibility issue**.

## Root Cause

The build uses Tycho 5.0.1 which includes dependencies compiled with Java 21 (class file version 65.0), but the build environment was using Java 17 (class file version 61.0).

### Error Details
```
java.lang.UnsupportedClassVersionError: org/eclipse/tycho/build/bnd/BndWorkspaceMapping 
has been compiled by a more recent version of the Java Runtime (class file version 65.0), 
this version of the Java Runtime only recognizes class file versions up to 61.0
```

- Class file version 65.0 = Java 21
- Class file version 61.0 = Java 17

## Solution

### Update Build Environment to Java 21

The build requires **Java 21 or higher**. Update your build environment:

#### Option 1: Set JAVA_HOME
```bash
export JAVA_HOME=/path/to/jdk-21
export PATH=$JAVA_HOME/bin:$PATH
```

#### Option 2: Use Java Version Manager (if available)
```bash
sdk use java 21.0.x-tem
# or
jenv global 21
```

#### Option 3: Update CI/CD Configuration
For GitHub Actions:
```yaml
- name: Set up JDK 21
  uses: actions/setup-java@v4
  with:
    java-version: '21'
    distribution: 'temurin'
```

## Build Commands

Once Java 21 is configured, run:

```bash
# Clean build (skip tests for faster build)
mvn clean verify -DskipTests=true

# Full build with tests
mvn clean verify

# For individual bundle builds
mvn clean verify -Pbuild-individual-bundles
```

## Additional Requirements

### Network Access
The build requires network access to Eclipse Maven repositories:
- `https://repo.eclipse.org/content/repositories/eclipse/`

This is needed to download the parent POM:
- `org.eclipse:eclipse-platform-parent:4.39.0-SNAPSHOT`

Ensure firewall rules allow access to `repo.eclipse.org`.

## Verification

After the fix, you should see:
1. No `UnsupportedClassVersionError`
2. Maven successfully downloads dependencies
3. Build completes without errors

## Quick Check Script

```bash
#!/bin/bash
echo "Checking Java version..."
java -version 2>&1 | grep "version"
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)

if [ "$JAVA_VERSION" -ge 21 ]; then
    echo "✓ Java $JAVA_VERSION detected - compatible"
    echo "Running Maven build..."
    mvn clean verify -DskipTests=true
else
    echo "✗ Java $JAVA_VERSION detected - incompatible"
    echo "ERROR: Java 21 or higher is required"
    echo "Current Java version: $(java -version 2>&1 | head -n 1)"
    exit 1
fi
```

## Summary

**To fix this PR:**
1. ✅ Upgrade to Java 21 or higher
2. ✅ Ensure network access to repo.eclipse.org  
3. ✅ Run `mvn clean verify`

That's it! The build should now work correctly.
