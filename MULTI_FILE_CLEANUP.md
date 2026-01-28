# Multi-File Cleanup API

## Overview

The multi-file cleanup API extends Eclipse JDT's cleanup infrastructure to support coordinated changes across multiple compilation units in a single atomic operation. This enables more powerful code transformations such as:

- Removing unused methods along with their interface declarations
- Migrating APIs across multiple files (e.g., JUnit 4 → 5)
- Modernizing code patterns that span multiple classes
- Removing dead code across an entire codebase

**New in 1.22**: The API now supports selective change acceptance with dependency tracking, allowing users to review and accept/reject individual changes while maintaining consistency.

## Key Components

### IMultiFileCleanUp Interface

The main extension point is the `IMultiFileCleanUp` interface located in `org.eclipse.jdt.ui.cleanup`:

```java
public interface IMultiFileCleanUp extends ICleanUp {
    /**
     * Creates a fix across multiple compilation units.
     * 
     * @param contexts List of CleanUpContext objects, one per file
     * @return CompositeChange describing all coordinated edits; may return null if no change required
     */
    CompositeChange createFix(List<CleanUpContext> contexts) throws CoreException;
    
    /**
     * Creates independent changes that can be individually accepted or rejected.
     * Default implementation wraps createFix() result.
     */
    default List<IndependentChange> createIndependentFixes(List<CleanUpContext> contexts) 
            throws CoreException;
    
    /**
     * Recomputes remaining changes after user selection with fresh ASTs.
     * Only called if requiresFreshASTAfterSelection() returns true.
     */
    default CompositeChange recomputeAfterSelection(
            List<IndependentChange> selectedChanges,
            List<CleanUpContext> freshContexts) throws CoreException;
    
    /**
     * Returns true if this cleanup requires fresh AST recomputation after each change selection.
     */
    default boolean requiresFreshASTAfterSelection();
}
```

### IndependentChange Interface

Represents a change that can be independently accepted or rejected:

```java
public interface IndependentChange {
    /**
     * @return true if this change can be rejected without affecting other changes
     */
    boolean isIndependent();
    
    /**
     * @return list of other changes that depend on this change
     */
    List<IndependentChange> getDependentChanges();
    
    /**
     * @return the underlying LTK Change object
     */
    Change getChange();
    
    /**
     * @return human-readable description of this change
     */
    String getDescription();
}
```

### Infrastructure Support

The `CleanUpRefactoring` class has been enhanced to:
1. Detect cleanups implementing `IMultiFileCleanUp`
2. Collect all compilation unit contexts before processing
3. Invoke multi-file cleanups with all contexts at once
4. Maintain backward compatibility with existing `ICleanUp` implementations

## Usage

### Creating a Multi-File Cleanup

Extend `AbstractCleanUp` and implement `IMultiFileCleanUp`:

```java
public class MyMultiFileCleanUp extends AbstractCleanUp implements IMultiFileCleanUp {
    
    @Override
    public CleanUpRequirements getRequirements() {
        // Specify if you need AST, fresh AST, compiler options, etc.
        return new CleanUpRequirements(requiresAST, requiresFreshAST, 
                                       requiresChangedRegions, compilerOptions);
    }
    
    @Override
    public CompositeChange createFix(List<CleanUpContext> contexts) throws CoreException {
        CompositeChange composite = new CompositeChange("My Multi-File Fix");
        
        // Analyze all contexts together
        for (CleanUpContext context : contexts) {
            ICompilationUnit cu = context.getCompilationUnit();
            CompilationUnit ast = context.getAST(); // may be null if not required
            
            // Analyze and create changes
            CompilationUnitChange change = createChangeFor(cu, ast);
            if (change != null) {
                composite.add(change);
            }
        }
        
        return composite.getChildren().length > 0 ? composite : null;
    }
    
    @Override
    public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
        // Fallback for single-file invocation (usually return null)
        return null;
    }
    
    // Implement other ICleanUp methods...
}
```

### Example Use Case: Removing Unused Methods

A practical example would be a cleanup that removes unused methods along with their declarations in interfaces:

1. **Analyze** all compilation units to find unused methods
2. **Identify** related declarations in interfaces, abstract classes, and subclasses
3. **Create** text edits for all affected files
4. **Bundle** all changes into a single `CompositeChange`
5. **Preview** and **apply** all changes atomically with unified undo

## Selective Change Acceptance (New in 1.22)

### Creating Independent Changes

To support selective change acceptance, implement `createIndependentFixes()`:

```java
public class MyMultiFileCleanUp extends AbstractCleanUp implements IMultiFileCleanUp {
    
    @Override
    public List<IndependentChange> createIndependentFixes(List<CleanUpContext> contexts) 
            throws CoreException {
        List<IndependentChange> changes = new ArrayList<>();
        
        // Create independent changes - each can be accepted/rejected individually
        for (CleanUpContext context : contexts) {
            CompilationUnitChange change = createChangeFor(context);
            if (change != null) {
                // true = this change is independent
                changes.add(new IndependentChangeImpl(change, true));
            }
        }
        
        return changes;
    }
}
```

### Tracking Change Dependencies

When changes depend on each other, mark the dependencies:

```java
@Override
public List<IndependentChange> createIndependentFixes(List<CleanUpContext> contexts) 
        throws CoreException {
    List<IndependentChange> changes = new ArrayList<>();
    
    // Change A: Remove interface method declaration
    Change interfaceChange = removeInterfaceMethod(contexts);
    IndependentChangeImpl changeA = new IndependentChangeImpl(interfaceChange, false);
    changes.add(changeA);
    
    // Change B: Remove implementation - depends on A
    Change implementationChange = removeImplementation(contexts);
    IndependentChangeImpl changeB = new IndependentChangeImpl(implementationChange, false);
    
    // Establish dependency: B depends on A
    changeA.addDependentChange(changeB);
    changes.add(changeB);
    
    return changes;
}
```

**Key Points:**
- Set `independent = false` for changes that have dependencies
- Use `addDependentChange()` to establish dependency relationships
- The preview UI will show dependencies and warn users about rejecting dependent changes

### Recomputing After Selection

For cleanups where later changes depend on earlier ones being applied:

```java
@Override
public boolean requiresFreshASTAfterSelection() {
    // Return true if changes need to be recomputed after user selection
    return true;
}

@Override
public CompositeChange recomputeAfterSelection(
        List<IndependentChange> selectedChanges,
        List<CleanUpContext> freshContexts) throws CoreException {
    
    // Recompute remaining changes with fresh ASTs
    CompositeChange recomputed = new CompositeChange("Remaining Changes");
    
    for (CleanUpContext context : freshContexts) {
        // Analyze fresh AST to determine what changes are still needed
        CompilationUnitChange change = createChangeFor(context);
        if (change != null) {
            recomputed.add(change);
        }
    }
    
    return recomputed.getChildren().length > 0 ? recomputed : null;
}
```

**When to use `requiresFreshASTAfterSelection()`:**
- ✓ Changes interact with each other (e.g., removing a method affects override detection)
- ✓ Validity of later changes depends on earlier changes being applied
- ✗ Changes are completely independent (performance cost of re-parsing)
- ✗ All changes can be computed upfront without conflicts

### Independent vs Dependent Changes

**Independent Changes** - Can be safely rejected:
- Adding @Override annotations to different methods
- Formatting changes in different files
- Adding missing null checks to unrelated methods
- Removing unused imports from different files

**Dependent Changes** - Require coordination:
- Removing a method from interface + all implementations
- Renaming a field + all its references
- Extracting a constant + replacing all occurrences
- Moving a class + updating all import statements

## Backward Compatibility

The API is fully backward compatible:
- Existing `ICleanUp` implementations continue to work unchanged
- Legacy cleanups are processed per-file as before
- Multi-file cleanups are detected via `instanceof IMultiFileCleanUp`
- Mixed cleanup sessions (both types) are supported

## Benefits

1. **Atomic Operations**: All changes are previewed and applied together
2. **Unified Undo**: One undo operation reverts all coordinated changes
3. **Safer Refactoring**: Analyze relationships across files before making changes
4. **Better User Experience**: Single preview shows all related changes
5. **Extensibility**: Supports future advanced cleanup scenarios

## Testing

See `MultiFileCleanUpTest` for examples of testing multi-file cleanups. The test framework supports:
- Creating test compilation units
- Applying multi-file cleanups
- Verifying changes across multiple files
- Testing interaction with regular cleanups

## Implementation Notes

- Multi-file cleanups are processed **before** regular cleanups in each project
- AST parsing is optimized - only performed if required by cleanup requirements
- Errors in one multi-file cleanup don't prevent others from running
- Progress monitoring is integrated with existing cleanup progress reporting
- **Change Independence**: The framework uses the default `createIndependentFixes()` implementation for backward compatibility
- **Dependency Tracking**: Dependencies are tracked using the `IndependentChange` interface and `IndependentChangeImpl` helper class
- **Recomputation**: Fresh AST recomputation is optional and controlled by `requiresFreshASTAfterSelection()`

## API Design Principles

### Backward Compatibility

All new methods in `IMultiFileCleanUp` have default implementations:
- Existing implementations continue to work without modification
- Default `createIndependentFixes()` wraps `createFix()` result as a single independent change
- Default `recomputeAfterSelection()` calls `createFix()` with fresh contexts
- Default `requiresFreshASTAfterSelection()` returns false

### Performance Considerations

**Upfront Computation (Default)**:
- All changes computed once at the beginning
- Best performance for independent changes
- Changes shown in preview immediately

**Recomputation Mode** (`requiresFreshASTAfterSelection() = true`):
- AST re-parsed after each selection
- Changes recomputed based on current state
- Higher accuracy for dependent changes
- Performance cost: O(n) re-parsing where n = number of selection rounds

Choose recomputation only when necessary for correctness.

## Future Enhancements

### Implementing Custom Preview UI for Selective Acceptance

The API is ready for custom UI integration. Here's a guide for implementing an interactive preview with selective change acceptance:

#### Step 1: Extend CleanUpRefactoringWizard

Create a subclass that adds a selection page before the standard preview:

```java
public class SelectiveCleanUpWizard extends CleanUpRefactoringWizard {
    
    @Override
    protected void addUserInputPages() {
        super.addUserInputPages(); // Add configuration page
        
        // Add custom selection page if cleanups support it
        if (supportsSelectiveAcceptance()) {
            addPage(new IndependentChangeSelectionPage());
        }
    }
}
```

#### Step 2: Create Selection Page with Checkbox Tree

```java
public class IndependentChangeSelectionPage extends UserInputWizardPage {
    
    private CheckboxTreeViewer treeViewer;
    private List<IndependentChange> allChanges;
    private List<IndependentChange> selectedChanges;
    
    @Override
    public void createControl(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout());
        
        // Create tree viewer
        Tree tree = new Tree(composite, SWT.CHECK | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        tree.setLayoutData(new GridData(GridData.FILL_BOTH));
        treeViewer = new CheckboxTreeViewer(tree);
        
        // Set content and label providers
        treeViewer.setContentProvider(new IndependentChangeContentProvider());
        treeViewer.setLabelProvider(new IndependentChangeLabelProvider());
        
        // Add check state listener
        treeViewer.addCheckStateListener(event -> {
            IndependentChange change = (IndependentChange) event.getElement();
            if (!event.getChecked() && hasDependents(change)) {
                // Show warning dialog
                showDependencyWarning(change);
                event.getCheckable().setChecked(change, true); // Revert
            } else {
                updateSelection();
                if (requiresRecomputation()) {
                    recomputeChanges();
                }
            }
        });
        
        // Load initial changes
        loadInitialChanges();
        
        setControl(composite);
    }
    
    private void loadInitialChanges() {
        CleanUpRefactoring refactoring = (CleanUpRefactoring) getRefactoring();
        try {
            // Get independent changes from refactoring
            allChanges = refactoring.createIndependentChanges(...);
            treeViewer.setInput(allChanges);
            treeViewer.setAllChecked(true);
            selectedChanges = new ArrayList<>(allChanges);
        } catch (CoreException e) {
            JavaPlugin.log(e);
        }
    }
    
    private void showDependencyWarning(IndependentChange change) {
        List<IndependentChange> dependents = change.getDependentChanges();
        StringBuilder message = new StringBuilder();
        message.append("The following changes depend on this change:\n\n");
        for (IndependentChange dependent : dependents) {
            message.append("- ").append(dependent.getDescription()).append("\n");
        }
        message.append("\nDeselecting this change may cause errors. Continue?");
        
        MessageDialog.openWarning(getShell(), 
            "Dependency Warning", 
            message.toString());
    }
    
    private void recomputeChanges() {
        // Recompute with fresh ASTs after selection
        CleanUpRefactoring refactoring = (CleanUpRefactoring) getRefactoring();
        try {
            CompositeChange recomputed = refactoring.recomputeChangesAfterSelection(
                selectedChanges, freshContexts);
            // Update tree with recomputed changes
            updateTreeWithRecomputedChanges(recomputed);
        } catch (CoreException e) {
            JavaPlugin.log(e);
        }
    }
}
```

#### Step 3: Content Provider for Tree

```java
public class IndependentChangeContentProvider implements ITreeContentProvider {
    
    @Override
    public Object[] getElements(Object inputElement) {
        if (inputElement instanceof List) {
            return ((List<?>) inputElement).toArray();
        }
        return new Object[0];
    }
    
    @Override
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof IndependentChange) {
            IndependentChange change = (IndependentChange) parentElement;
            return change.getDependentChanges().toArray();
        }
        return new Object[0];
    }
    
    @Override
    public boolean hasChildren(Object element) {
        if (element instanceof IndependentChange) {
            return !((IndependentChange) element).getDependentChanges().isEmpty();
        }
        return false;
    }
}
```

#### Step 4: Label Provider with Dependency Icons

```java
public class IndependentChangeLabelProvider extends LabelProvider {
    
    private Image dependencyIcon;
    private Image independentIcon;
    
    @Override
    public String getText(Object element) {
        if (element instanceof IndependentChange) {
            IndependentChange change = (IndependentChange) element;
            return change.getDescription();
        }
        return super.getText(element);
    }
    
    @Override
    public Image getImage(Object element) {
        if (element instanceof IndependentChange) {
            IndependentChange change = (IndependentChange) element;
            return change.isIndependent() ? independentIcon : dependencyIcon;
        }
        return super.getImage(element);
    }
}
```

#### Step 5: Filtering Changes Before Preview

Override `performFinish()` to apply only selected changes:

```java
@Override
public boolean performFinish() {
    // Filter refactoring to only include selected changes
    CleanUpRefactoring refactoring = (CleanUpRefactoring) getRefactoring();
    refactoring.setSelectedChanges(selectedChanges);
    
    return super.performFinish();
}
```

### Design Considerations

1. **Performance**: Cache ASTs where possible, only recompute when necessary
2. **User Experience**: Show progress indicators during recomputation
3. **Error Handling**: Gracefully handle cleanup errors, don't block entire wizard
4. **Accessibility**: Ensure keyboard navigation works in tree viewer
5. **Help Context**: Add F1 help documentation for the selection page

### Integration Points

The existing infrastructure provides these integration points:

- `CleanUpRefactoring.requiresFreshASTAfterSelection()` - Check if recomputation needed
- `CleanUpRefactoring.createIndependentChanges()` - Get independent changes
- `CleanUpRefactoring.recomputeChangesAfterSelection()` - Recompute after selection
- `IndependentChange.getDependentChanges()` - Get dependency information
- `IndependentChange.isIndependent()` - Check if change can be safely rejected

### Testing Custom UI

Test your custom UI implementation with:

```java
@Test
public void testCustomSelectionUI() {
    // Create cleanup with independent changes
    IMultiFileCleanUp cleanup = new TestCleanupWithIndependentChanges();
    refactoring.addCleanUp(cleanup);
    
    // Open wizard
    SelectiveCleanUpWizard wizard = new SelectiveCleanUpWizard(refactoring);
    WizardDialog dialog = new WizardDialog(shell, wizard);
    
    // Verify selection page is shown
    IWizardPage[] pages = wizard.getPages();
    assertTrue(pages[1] instanceof IndependentChangeSelectionPage);
    
    // Simulate user deselecting a change
    IndependentChangeSelectionPage page = (IndependentChangeSelectionPage) pages[1];
    page.deselectChange(changeToReject);
    
    // Verify dependency warning is shown
    // Verify recomputation is triggered
    // Verify final changes match selection
}
```

---

## Future Enhancements (Beyond Custom UI)

Potential future improvements:
- UI for configuring multi-file cleanup preferences
- Extension point for third-party multi-file cleanups
- Performance optimizations for large codebases
- Integration with Save Actions
- Batch processing with selective acceptance
- Change preview with syntax highlighting in selection page

## See Also

- `ICleanUp` - Base cleanup interface
- `CleanUpContext` - Context passed to cleanups
- `CompositeChange` - LTK change aggregation
- `CleanUpRefactoring` - Cleanup orchestration
- `IndependentChange` - Interface for selective change acceptance
- `IndependentChangeImpl` - Helper class for creating independent changes with dependencies
