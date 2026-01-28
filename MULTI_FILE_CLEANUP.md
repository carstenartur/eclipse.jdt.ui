# Multi-File Cleanup API

## Overview

The Multi-File Cleanup API extends Eclipse JDT's cleanup framework to support advanced features for multi-file refactoring operations:

1. **Selective Change Acceptance** - Users can accept or reject individual changes in the preview
2. **Dependency Tracking** - Changes can declare dependencies on other changes
3. **Fresh AST Re-Analysis** - AST can be re-parsed when user selections change
4. **Iterative Workflows** - Support for compute → preview → select → recompute cycles

## Core Interfaces

### `IMultiFileCleanUp`

An extension of `ICleanUp` that adds support for multi-file cleanup operations with fine-grained control.

```java
public interface IMultiFileCleanUp extends ICleanUp {
    /**
     * Returns whether this cleanup requires fresh AST parsing after user selections change.
     */
    default boolean requiresFreshASTAfterSelection() {
        return false;
    }

    /**
     * Creates independent changes that can be selectively accepted or rejected.
     */
    default Collection<IndependentChange> createIndependentChanges(
            Collection<CleanUpContext> contexts, IProgressMonitor monitor) throws CoreException {
        return List.of();
    }

    /**
     * Recomputes changes after user selections with fresh AST contexts.
     */
    default Collection<IndependentChange> recomputeChangesAfterSelection(
            Collection<CleanUpContext> contexts,
            Collection<IndependentChange> selectedChanges,
            IProgressMonitor monitor) throws CoreException {
        return List.of();
    }
}
```

### `IndependentChange`

Represents a single, independent change that can be selectively applied.

```java
public interface IndependentChange {
    /**
     * Returns the underlying LTK Change object.
     */
    Change getChange();

    /**
     * Returns a description for display in the UI.
     */
    default String getDescription() {
        return getChange().getName();
    }

    /**
     * Returns changes that depend on this change.
     */
    default Collection<IndependentChange> getDependentChanges() {
        return Collections.emptyList();
    }

    /**
     * Returns whether this change is selected for application.
     */
    boolean isSelected();

    /**
     * Sets whether this change is selected.
     */
    void setSelected(boolean selected);
}
```

## Implementation Guide

### Basic Multi-File Cleanup

To create a cleanup that supports selective acceptance:

```java
public class MyMultiFileCleanUp extends AbstractMultiFix implements IMultiFileCleanUp {

    @Override
    public Collection<IndependentChange> createIndependentChanges(
            Collection<CleanUpContext> contexts, IProgressMonitor monitor) throws CoreException {
        
        List<IndependentChange> changes = new ArrayList<>();
        
        for (CleanUpContext context : contexts) {
            // Analyze the compilation unit
            ICleanUpFix fix = createFix(context);
            if (fix != null) {
                // Create an independent change for each fix
                Change change = fix.createChange(monitor);
                changes.add(new IndependentChangeImpl(change));
            }
        }
        
        return changes;
    }
}
```

### With Fresh AST Re-Analysis

For cleanups that need to recompute after user selections:

```java
public class MyAdvancedCleanUp extends AbstractMultiFix implements IMultiFileCleanUp {

    @Override
    public boolean requiresFreshASTAfterSelection() {
        // Return true if your cleanup needs fresh AST after user selections
        return true;
    }

    @Override
    public Collection<IndependentChange> recomputeChangesAfterSelection(
            Collection<CleanUpContext> contexts,
            Collection<IndependentChange> selectedChanges,
            IProgressMonitor monitor) throws CoreException {
        
        // Re-analyze with fresh AST
        List<IndependentChange> recomputedChanges = new ArrayList<>();
        
        for (CleanUpContext context : contexts) {
            // The context now has a fresh AST reflecting accepted changes
            ICleanUpFix fix = createFix(context);
            if (fix != null) {
                Change change = fix.createChange(monitor);
                recomputedChanges.add(new IndependentChangeImpl(change));
            }
        }
        
        return recomputedChanges;
    }
}
```

### With Dependency Tracking

To declare dependencies between changes:

```java
@Override
public Collection<IndependentChange> createIndependentChanges(
        Collection<CleanUpContext> contexts, IProgressMonitor monitor) throws CoreException {
    
    List<IndependentChangeImpl> changes = new ArrayList<>();
    
    // Create base change
    IndependentChangeImpl baseChange = new IndependentChangeImpl(createBaseChange());
    changes.add(baseChange);
    
    // Create dependent change
    IndependentChangeImpl dependentChange = new IndependentChangeImpl(createDependentChange());
    
    // Declare the dependency
    baseChange.addDependentChange(dependentChange);
    
    changes.add(dependentChange);
    
    return changes;
}
```

## User Experience

### In the Preview Dialog

When using the multi-file cleanup API:

1. **Selection** - Users can check/uncheck individual changes
2. **Dependency Warnings** - A warning appears when unchecking a change that others depend on
3. **Recomputation** - If needed, the cleanup automatically recomputes remaining changes
4. **Iteration** - Users can iterate: select → recompute → select again

### Example Workflow

```
1. Run cleanup                    → Initial changes computed
2. Preview dialog shows changes   → User unchecks some changes
3. [If requiresFreshASTAfterSelection()]
   → AST is re-parsed
   → recomputeChangesAfterSelection() is called
   → Preview updates with new changes
4. User accepts remaining changes → Changes are applied
```

## When to Use Each Feature

### Selective Acceptance (`createIndependentChanges`)

Use when:
- Your cleanup makes multiple independent modifications
- Users should be able to pick and choose which changes to apply
- Changes can be safely applied individually

Example: Renaming multiple variables across files - each rename is independent.

### Fresh AST Re-Analysis (`requiresFreshASTAfterSelection`)

Use when:
- The correctness of remaining changes depends on which changes were accepted
- Rejecting one change may create opportunities for new changes
- The AST structure changes significantly based on user selections

Example: Removing unused imports - if user rejects removing method A, imports used by A should remain.

### Dependency Tracking (`getDependentChanges`)

Use when:
- Some changes require other changes to be applied first
- Applying changes out of order would break compilation
- You want to warn users about implicit dependencies

Example: Refactoring where renaming a method requires updating all its callers.

## Best Practices

1. **Keep Changes Small** - Create fine-grained independent changes when possible
2. **Avoid Circular Dependencies** - Don't create dependency cycles between changes
3. **Cache Expensive Computations** - Store results that don't change between recomputations
4. **Provide Good Descriptions** - Override `getDescription()` to give clear, concise change descriptions
5. **Handle Edge Cases** - Test what happens when all changes are rejected
6. **Backward Compatibility** - Implement default methods so cleanups work without multi-file features

## Implementation Classes

- **`IndependentChangeImpl`** - Default implementation of `IndependentChange`
- **`CleanUpRefactoring`** - Orchestrates the cleanup workflow
  - `requiresFreshASTAfterSelection()` - Checks if any cleanup needs fresh AST
  - `createIndependentChanges()` - Collects changes from all cleanups
  - `recomputeChangesAfterSelection()` - Triggers recomputation across all cleanups

## Testing

Test your multi-file cleanup implementation:

```java
@Test
public void testSelectiveAcceptance() throws Exception {
    // Setup: create a cleanup that supports selective acceptance
    IMultiFileCleanUp cleanup = new MyMultiFileCleanUp();
    
    // Execute: create independent changes
    Collection<IndependentChange> changes = cleanup.createIndependentChanges(contexts, monitor);
    
    // Verify: changes can be individually selected
    assertTrue(changes.size() > 1);
    
    // Deselect some changes
    changes.iterator().next().setSelected(false);
    
    // If fresh AST is required, recompute
    if (cleanup.requiresFreshASTAfterSelection()) {
        Collection<IndependentChange> recomputed = 
            cleanup.recomputeChangesAfterSelection(freshContexts, selectedChanges, monitor);
        assertNotNull(recomputed);
    }
}
```

## Future Enhancements

Potential future additions:

- **Change Previews** - Visual diff for each independent change
- **Undo Support** - Allow users to undo applied changes
- **Conflict Detection** - Automatically detect conflicting changes
- **Batch Operations** - Select/deselect groups of related changes
- **Persistence** - Remember user preferences for future cleanups

## See Also

- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - Technical implementation details
- JDT Cleanup Framework Documentation
- Eclipse LTK Refactoring Framework
