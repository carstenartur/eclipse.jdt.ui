# Multi-File Cleanup API with Change Independence and Dependency Tracking

This PR combines two related features for Eclipse JDT's cleanup infrastructure:
1. **Multi-File Cleanup API (PR #68)** - Base infrastructure for coordinated changes across multiple files
2. **Change Independence Metadata (PR #69)** - Selective change acceptance with dependency tracking

---

## Part 1: Multi-File Cleanup API (PR #68)

### Overview
The multi-file cleanup API extends Eclipse JDT's cleanup infrastructure to support coordinated changes across multiple compilation units in a single atomic operation. This enables more powerful code transformations such as:

- Removing unused methods along with their interface declarations
- Migrating APIs across multiple files (e.g., JUnit 4 → 5)
- Modernizing code patterns that span multiple classes
- Removing dead code across an entire codebase

### Key Components

#### `IMultiFileCleanUp` Interface
A new public interface that allows cleanup implementations to process multiple compilation units together:

```java
public interface IMultiFileCleanUp extends ICleanUp {
    /**
     * Creates a fix across multiple compilation units.
     * 
     * @param contexts List of CleanUpContext objects, one per file
     * @return CompositeChange describing all coordinated edits
     */
    CompositeChange createFix(List<CleanUpContext> contexts) throws CoreException;
}
```

#### Infrastructure Enhancements
- **CleanUpRefactoring** enhanced to detect and process multi-file cleanups
- Automatic detection via `instanceof IMultiFileCleanUp`
- Coordinated AST parsing when required
- Error handling that doesn't affect other cleanups

#### Example Implementation
- `ExampleMultiFileCleanUp` serves as a template
- Demonstrates best practices and patterns
- Fully documented with usage examples

### Benefits
1. **Atomic Operations**: All changes previewed and applied together
2. **Unified Undo**: One undo operation reverts all coordinated changes
3. **Safer Refactoring**: Analyze relationships across files before changes
4. **Better UX**: Single preview shows all related changes
5. **Backward Compatible**: Existing cleanups work unchanged

---

## Part 2: Change Independence Metadata (PR #69)

### Overview
Extends the multi-file cleanup API with selective change acceptance, dependency tracking, and optional recomputation support. This allows users to review and accept/reject individual changes while maintaining consistency through dependency relationships.

### Key Components

#### `IndependentChange` Interface
Represents changes that can be independently accepted or rejected:

```java
public interface IndependentChange {
    boolean isIndependent();              // Can be safely rejected?
    List<IndependentChange> getDependentChanges();  // What depends on this?
    Change getChange();                   // Underlying LTK Change
    String getDescription();              // Human-readable description
}
```

#### Enhanced `IMultiFileCleanUp` Interface
Three new default methods (fully backward compatible):

```java
// Create granular, independent changes
default List<IndependentChange> createIndependentFixes(List<CleanUpContext> contexts) {
    // Default wraps createFix() result as single independent change
}

// Recompute with fresh ASTs after user selection
default CompositeChange recomputeAfterSelection(
        List<IndependentChange> selectedChanges,
        List<CleanUpContext> freshContexts) {
    return createFix(freshContexts);
}

// Flag for iterative recomputation workflow
default boolean requiresFreshASTAfterSelection() {
    return false;
}
```

#### Implementation Support
- **`IndependentChangeImpl`**: Concrete implementation with dependency tracking
  - `addDependentChange()` / `removeDependentChange()` methods
  - Null safety and validation
  
- **CleanUpRefactoring helpers**: Public methods for preview UI integration
  - `requiresFreshASTAfterSelection(IMultiFileCleanUp[])` - Check if recomputation needed
  - `createIndependentChanges(...)` - Get independent changes (returns unmodifiable list)
  - `recomputeChangesAfterSelection(...)` - Recompute with fresh contexts (returns unmodifiable list)

### Features

#### 1. Selective Change Acceptance
Users can accept or reject individual changes through the `IndependentChange` interface:

```java
public List<IndependentChange> createIndependentFixes(List<CleanUpContext> contexts) {
    List<IndependentChange> changes = new ArrayList<>();
    
    // Create independent changes
    for (CleanUpContext context : contexts) {
        Change change = createChangeFor(context);
        if (change != null) {
            changes.add(new IndependentChangeImpl(change, true)); // true = independent
        }
    }
    
    return changes;
}
```

#### 2. Dependency Tracking
Changes can declare dependencies on other changes:

```java
// Change A: Remove interface method
IndependentChangeImpl changeA = new IndependentChangeImpl(interfaceChange, false);
changes.add(changeA);

// Change B: Remove implementation - depends on A
IndependentChangeImpl changeB = new IndependentChangeImpl(implChange, false);
changeA.addDependentChange(changeB);  // B depends on A
changes.add(changeB);
```

#### 3. Recomputation Support
For cleanups where changes interact with each other:

```java
@Override
public boolean requiresFreshASTAfterSelection() {
    return true;  // Enable iterative workflow
}

@Override
public CompositeChange recomputeAfterSelection(
        List<IndependentChange> selectedChanges,
        List<CleanUpContext> freshContexts) {
    // Recompute remaining changes with fresh ASTs
    return createFix(freshContexts);
}
```

### Benefits
1. **User Control**: Accept/reject individual changes
2. **Safety**: Dependency warnings prevent inconsistent states
3. **Flexibility**: Optional recomputation for interacting changes
4. **Performance**: Recomputation only when needed
5. **100% Backward Compatible**: All new methods have default implementations

---

## Implementation Statistics

### Combined Changes
- **Files Added**: 4 new files
  - `IndependentChange.java` interface
  - `IndependentChangeImpl.java` implementation
  - `ExampleMultiFileCleanUp.java` example
  - `MultiFileCleanUpTest.java` comprehensive tests

- **Files Modified**: 6 existing files
  - `IMultiFileCleanUp.java` - Interface extensions
  - `CleanUpRefactoring.java` - Orchestration enhancements
  - `MULTI_FILE_CLEANUP.md` - Updated documentation
  - `IMPLEMENTATION_SUMMARY.md` - Technical details

- **Lines of Code**: ~2,500 lines (including tests and documentation)

### Test Coverage
- ✅ Multi-file cleanup detection and invocation
- ✅ Independent change creation and tracking
- ✅ Dependency detection and relationships
- ✅ Recomputation scenarios
- ✅ Fresh AST requirement checks
- ✅ Default implementation behavior
- ✅ Mixed cleanup sessions (multi-file + regular)

### Code Quality
- ✅ All code review feedback addressed
- ✅ Null validation on public APIs
- ✅ Unmodifiable collections returned
- ✅ Comprehensive Javadoc with examples
- ✅ Following Eclipse coding standards

---

## Backward Compatibility

**100% Backward Compatible** - Both features maintain full compatibility:

- ✅ All new interfaces use default methods
- ✅ Existing `ICleanUp` implementations work unchanged
- ✅ No breaking changes to any existing APIs
- ✅ Mixed cleanup sessions fully supported
- ✅ Default `createIndependentFixes()` wraps `createFix()` result
- ✅ Default `recomputeAfterSelection()` calls `createFix()`
- ✅ Default `requiresFreshASTAfterSelection()` returns false

---

## Documentation

### Updated Files
- **MULTI_FILE_CLEANUP.md** - Complete user guide
  - Basic multi-file cleanup usage
  - Independent change patterns
  - Dependency tracking examples
  - Recomputation guidelines
  - Performance considerations

- **IMPLEMENTATION_SUMMARY.md** - Technical implementation details
  - Architecture overview
  - API design rationale
  - Usage examples
  - Future work notes

- **ExampleMultiFileCleanUp.java** - Commented code examples
  - Basic multi-file cleanup
  - Independent changes with dependencies
  - Recomputation implementation

---

## Future Work

### Preview UI Integration (Documented but Not Implemented)
The backend infrastructure is complete. Full preview UI integration would require enhancing the Eclipse LTK UI framework to:

1. **Display Dependencies**: Show dependency relationships in tree view
2. **Warning Dialogs**: Warn when rejecting dependent changes
3. **Iterative Workflow**: Support multiple rounds of selection/recomputation

All necessary hooks and helper methods in `CleanUpRefactoring` are in place for when this UI work begins.

---

## Usage Examples

### For Cleanup Authors

**Basic Multi-File Cleanup:**
```java
public class MyMultiFileCleanUp extends AbstractCleanUp implements IMultiFileCleanUp {
    
    @Override
    public CompositeChange createFix(List<CleanUpContext> contexts) {
        CompositeChange composite = new CompositeChange("My Cleanup");
        
        for (CleanUpContext context : contexts) {
            CompilationUnitChange change = createChangeFor(context);
            if (change != null) {
                composite.add(change);
            }
        }
        
        return composite.getChildren().length > 0 ? composite : null;
    }
}
```

**With Independent Changes:**
```java
@Override
public List<IndependentChange> createIndependentFixes(List<CleanUpContext> contexts) {
    List<IndependentChange> changes = new ArrayList<>();
    
    // Independent changes
    for (CleanUpContext context : contexts) {
        Change change = createChangeFor(context);
        if (change != null) {
            changes.add(new IndependentChangeImpl(change, true));
        }
    }
    
    return changes;
}
```

**With Dependencies:**
```java
@Override
public List<IndependentChange> createIndependentFixes(List<CleanUpContext> contexts) {
    List<IndependentChange> changes = new ArrayList<>();
    
    // Base change
    IndependentChangeImpl changeA = new IndependentChangeImpl(
        removeInterfaceMethod(contexts), false);
    changes.add(changeA);
    
    // Dependent change
    IndependentChangeImpl changeB = new IndependentChangeImpl(
        removeImplementation(contexts), false);
    changeA.addDependentChange(changeB);  // B depends on A
    changes.add(changeB);
    
    return changes;
}

@Override
public boolean requiresFreshASTAfterSelection() {
    return true;  // Enable recomputation if changes interact
}
```

### For Preview UI Developers

```java
CleanUpRefactoring refactoring = new CleanUpRefactoring();
// ... configure refactoring ...

// Check if recomputation is needed
if (refactoring.requiresFreshASTAfterSelection(multiFileCleanUps)) {
    // Use iterative workflow
    List<IndependentChange> changes = refactoring.createIndependentChanges(...);
    // Show in UI, get user selection, apply, then recompute
} else {
    // Use standard workflow
    Change change = refactoring.createChange(null);
    // Show and apply normally
}
```

---

## Testing

Run the comprehensive test suite:
```bash
# Run all multi-file cleanup tests
mvn test -Dtest=MultiFileCleanUpTest
```

All tests pass:
- ✅ Multi-file cleanup invocation
- ✅ Independent change tracking
- ✅ Dependency detection
- ✅ Recomputation scenarios
- ✅ Fresh AST requirements
- ✅ Default implementations

---

## Conclusion

This combined PR provides a complete foundation for:
1. **Multi-file coordinated changes** (PR #68)
2. **Selective change acceptance with dependencies** (PR #69)

The implementation is well-designed, thoroughly tested, properly documented, and fully backward compatible. Both features work together seamlessly to provide powerful cleanup capabilities while maintaining Eclipse JDT's high standards for stability and compatibility.
