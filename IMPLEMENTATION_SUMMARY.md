# Multi-File Cleanup Implementation Summary

## Overview

This document provides technical details about the implementation of the multi-file cleanup advanced features in Eclipse JDT.

## Architecture

### Component Layers

```
┌─────────────────────────────────────────┐
│           UI Layer                      │
│  CleanUpRefactoringWizard              │
│  Preview Dialog (future enhancement)    │
└──────────────┬──────────────────────────┘
               │
┌──────────────┴──────────────────────────┐
│        Refactoring Layer                │
│     CleanUpRefactoring                  │
│  - requiresFreshASTAfterSelection()     │
│  - createIndependentChanges()           │
│  - recomputeChangesAfterSelection()     │
└──────────────┬──────────────────────────┘
               │
┌──────────────┴──────────────────────────┐
│          Cleanup Layer                  │
│      IMultiFileCleanUp                  │
│  (extends ICleanUp)                     │
└──────────────┬──────────────────────────┘
               │
┌──────────────┴──────────────────────────┐
│         Change Layer                    │
│      IndependentChange                  │
│   IndependentChangeImpl                 │
└─────────────────────────────────────────┘
```

## Key Classes and Interfaces

### 1. `IndependentChange` Interface

**Location:** `org.eclipse.jdt.core.manipulation/common/org/eclipse/jdt/ui/cleanup/IndependentChange.java`

**Purpose:** Represents a single change that can be independently accepted or rejected.

**Key Methods:**
- `Change getChange()` - Returns the underlying LTK Change
- `String getDescription()` - Returns UI description
- `Collection<IndependentChange> getDependentChanges()` - Returns dependent changes
- `boolean isSelected()` / `void setSelected(boolean)` - Selection state

**Design Decisions:**
- Uses default methods for backward compatibility
- Minimal interface to keep implementation simple
- Wraps LTK Change instead of extending it (composition over inheritance)

### 2. `IndependentChangeImpl` Class

**Location:** `org.eclipse.jdt.ui/core extension/org/eclipse/jdt/internal/corext/fix/IndependentChangeImpl.java`

**Purpose:** Default implementation of `IndependentChange`.

**Key Features:**
- Maintains selection state
- Supports dependency tracking via `addDependentChange()`
- Immutable change reference
- Lazy initialization of dependent changes list

**Thread Safety:** Not thread-safe (assumes single-threaded UI context)

### 3. `IMultiFileCleanUp` Interface

**Location:** `org.eclipse.jdt.core.manipulation/common/org/eclipse/jdt/ui/cleanup/IMultiFileCleanUp.java`

**Purpose:** Extends `ICleanUp` with multi-file cleanup capabilities.

**Key Methods:**
- `boolean requiresFreshASTAfterSelection()` - Indicates need for AST re-parsing
- `Collection<IndependentChange> createIndependentChanges(...)` - Creates granular changes
- `Collection<IndependentChange> recomputeChangesAfterSelection(...)` - Recomputes after selection

**Design Decisions:**
- All methods have default implementations returning false/empty
- Cleanups can opt-in to features incrementally
- Maintains backward compatibility with existing `ICleanUp` implementations

### 4. Enhanced `CleanUpRefactoring` Class

**Location:** `org.eclipse.jdt.ui/core extension/org/eclipse/jdt/internal/corext/fix/CleanUpRefactoring.java`

**New Methods:**
```java
public boolean requiresFreshASTAfterSelection()
public Collection<IndependentChange> createIndependentChanges(Collection<CleanUpContext>, IProgressMonitor)
public Collection<IndependentChange> recomputeChangesAfterSelection(Collection<CleanUpContext>, Collection<IndependentChange>, IProgressMonitor)
```

**Implementation Strategy:**
- Aggregates results from all registered cleanups
- Returns true if any cleanup requires fresh AST
- Collects independent changes from all multi-file cleanups
- Delegates recomputation to individual cleanups

## Workflow

### Standard Cleanup (No Recomputation)

```
1. User initiates cleanup
2. CleanUpRefactoring.checkFinalConditions() called
3. For each cleanup:
   a. Create CleanUpContext (with AST if required)
   b. Call cleanup.createFix(context)
   c. Accumulate changes
4. Return composite change
5. User previews/applies changes
```

### Enhanced Cleanup (With Selective Acceptance)

```
1. User initiates cleanup
2. CleanUpRefactoring creates initial changes
3. Check requiresFreshASTAfterSelection()
4. If true:
   a. Call createIndependentChanges() for all cleanups
   b. Present changes in preview UI
   c. User selects/deselects changes
   d. If selections change:
      i. Re-parse AST with selected changes applied
      ii. Call recomputeChangesAfterSelection()
      iii. Update preview with new changes
   e. Repeat until user confirms
5. Apply selected changes
```

### Dependency Handling

```
1. User unchecks a change C1
2. System checks getDependentChanges() for C1
3. If C1 has dependents:
   a. Show warning dialog listing dependent changes
   b. Options:
      - Cancel: Keep C1 selected
      - Continue: Deselect C1 and all its dependents
      - Review: Show dependent changes in preview
4. Update selections based on user choice
5. If requiresFreshASTAfterSelection() is true, recompute
```

## Data Flow

### Creating Independent Changes

```
CleanUpRefactoring
  └─> For each IMultiFileCleanUp
       └─> createIndependentChanges(contexts)
            └─> For each context
                 └─> createFix(context)
                      └─> Create IndependentChangeImpl wrapping fix.createChange()
```

### Recomputation Flow

```
User deselects change
  └─> CleanUpRefactoring.recomputeChangesAfterSelection()
       └─> Apply selected changes to working copies
       └─> Re-parse ASTs
       └─> Create fresh CleanUpContexts
       └─> For each IMultiFileCleanUp
            └─> recomputeChangesAfterSelection(freshContexts, selectedChanges)
                 └─> Analyze fresh AST
                 └─> Return new IndependentChanges
```

## Integration Points

### With Existing Cleanup Framework

The implementation maintains full backward compatibility:

- **Existing cleanups** continue to work without changes
- **ICleanUp implementations** are not affected
- **Default methods** in `IMultiFileCleanUp` ensure no-op behavior
- **CleanUpRefactoring** handles both old and new cleanup types

### With LTK Refactoring Framework

- **Change hierarchy** - `IndependentChange` wraps LTK `Change` objects
- **Preview dialogs** - Can display `IndependentChange` objects (future enhancement)
- **Undo/Redo** - Inherits from LTK Change infrastructure

### With AST Parser

- **Batch parsing** - Uses existing `ASTBatchParser` infrastructure
- **Working copies** - Leverages `ICompilationUnit.getWorkingCopy()`
- **AST caching** - Reuses `CleanUpFixpointIterator` patterns

## Performance Considerations

### AST Parsing

**Issue:** Re-parsing ASTs is expensive for large codebases.

**Mitigation:**
- Only re-parse when `requiresFreshASTAfterSelection()` returns true
- Cache ASTs where possible
- Use working copies to avoid file I/O
- Batch parse multiple files together

### Change Computation

**Issue:** Creating independent changes for all possible fixes can be slow.

**Mitigation:**
- Lazy computation - only create changes when needed
- Incremental updates - only recompute affected changes
- Progress reporting - keep UI responsive during long operations

### Memory Usage

**Issue:** Keeping multiple ASTs in memory increases heap usage.

**Mitigation:**
- Release working copies when done
- Clear AST references after recomputation
- Process files in batches for large projects

## Extension Points

### For Cleanup Authors

To support selective acceptance:

```java
public class MyCleanUp extends AbstractMultiFix implements IMultiFileCleanUp {
    @Override
    public Collection<IndependentChange> createIndependentChanges(
            Collection<CleanUpContext> contexts, IProgressMonitor monitor) {
        // Return fine-grained changes
    }
}
```

To support recomputation:

```java
public class MyAdvancedCleanUp extends AbstractMultiFix implements IMultiFileCleanUp {
    @Override
    public boolean requiresFreshASTAfterSelection() {
        return true;  // Opt-in to recomputation
    }
    
    @Override
    public Collection<IndependentChange> recomputeChangesAfterSelection(
            Collection<CleanUpContext> contexts,
            Collection<IndependentChange> selectedChanges,
            IProgressMonitor monitor) {
        // Re-analyze with fresh AST
    }
}
```

### For UI Developers

To enhance the preview dialog (future work):

```java
// Access independent changes from refactoring
CleanUpRefactoring refactoring = ...;
Collection<IndependentChange> changes = refactoring.createIndependentChanges(contexts, monitor);

// Display in tree viewer with dependency indicators
for (IndependentChange change : changes) {
    TreeItem item = new TreeItem(tree, SWT.NONE);
    item.setText(change.getDescription());
    item.setChecked(change.isSelected());
    
    // Show dependencies
    if (!change.getDependentChanges().isEmpty()) {
        item.setImage(dependencyIcon);
    }
}

// Handle selection changes
tree.addSelectionListener(e -> {
    IndependentChange change = ...;
    
    // Check for dependents
    if (!change.isSelected() && !change.getDependentChanges().isEmpty()) {
        showDependencyWarning(change);
    }
    
    // Recompute if needed
    if (refactoring.requiresFreshASTAfterSelection()) {
        Collection<IndependentChange> recomputed = 
            refactoring.recomputeChangesAfterSelection(freshContexts, selected, monitor);
        updatePreview(recomputed);
    }
});
```

## Testing Strategy

### Unit Tests

Test individual components in isolation:

```java
@Test
public void testIndependentChangeImpl() {
    Change mockChange = mock(Change.class);
    when(mockChange.getName()).thenReturn("Test Change");
    
    IndependentChangeImpl change = new IndependentChangeImpl(mockChange);
    
    assertTrue(change.isSelected());  // Default selected
    assertEquals("Test Change", change.getDescription());
    
    change.setSelected(false);
    assertFalse(change.isSelected());
}

@Test
public void testDependencyTracking() {
    IndependentChangeImpl base = new IndependentChangeImpl(createChange());
    IndependentChangeImpl dependent = new IndependentChangeImpl(createChange());
    
    base.addDependentChange(dependent);
    
    assertTrue(base.getDependentChanges().contains(dependent));
}
```

### Integration Tests

Test the full workflow:

```java
@Test
public void testSelectiveAcceptanceWorkflow() {
    // Setup cleanup
    IMultiFileCleanUp cleanup = new TestMultiFileCleanUp();
    CleanUpRefactoring refactoring = new CleanUpRefactoring();
    refactoring.addCleanUp(cleanup);
    
    // Execute
    Collection<CleanUpContext> contexts = createContexts();
    Collection<IndependentChange> changes = 
        refactoring.createIndependentChanges(contexts, monitor);
    
    // Verify
    assertTrue(changes.size() > 0);
    
    // Simulate user deselection
    changes.iterator().next().setSelected(false);
    
    // Recompute if needed
    if (refactoring.requiresFreshASTAfterSelection()) {
        Collection<IndependentChange> recomputed = 
            refactoring.recomputeChangesAfterSelection(contexts, changes, monitor);
        assertNotNull(recomputed);
    }
}
```

### UI Tests (Future)

Test preview dialog interactions:

```java
@Test
public void testDependencyWarning() {
    // Setup: Create changes with dependencies
    // Action: Uncheck a change that has dependents
    // Verify: Warning dialog appears
    // Action: Choose to continue
    // Verify: Dependent changes are also unchecked
}
```

## Known Limitations

1. **No UI Integration Yet** - The backend API is ready, but preview dialog enhancements are not implemented
2. **No Circular Dependency Detection** - Cleanups must ensure they don't create dependency cycles
3. **No Conflict Detection** - If two independent changes modify the same code, behavior is undefined
4. **Single-threaded** - Not designed for concurrent access
5. **Memory Intensive** - Keeping multiple AST versions can consume significant heap

## Future Enhancements

### High Priority

1. **Preview Dialog Integration** - Implement UI for selective acceptance
2. **Dependency Warnings** - Show alerts when unchecking changes with dependents
3. **Visual Dependency Graph** - Display change dependencies as a tree/graph

### Medium Priority

4. **Conflict Detection** - Identify overlapping changes
5. **Change Grouping** - Allow batch operations on related changes
6. **Progress Indicators** - Show recomputation progress
7. **Preference Persistence** - Remember user selections

### Low Priority

8. **Undo Integration** - Full undo/redo support for iterative workflow
9. **Change Impact Analysis** - Show what code will be affected
10. **Performance Profiling** - Identify and optimize slow cleanups

## Version History

- **1.21** - Initial implementation (current)
  - Added `IndependentChange` interface
  - Added `IMultiFileCleanUp` interface
  - Added `IndependentChangeImpl` class
  - Enhanced `CleanUpRefactoring` with new methods
  - Created documentation

## References

- [MULTI_FILE_CLEANUP.md](MULTI_FILE_CLEANUP.md) - User and developer guide
- Eclipse JDT Core Manipulation documentation
- Eclipse LTK Refactoring Framework documentation
- JDT Cleanup Framework existing architecture
