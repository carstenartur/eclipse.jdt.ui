# Multi-File Cleanup API

## Overview

The multi-file cleanup API extends Eclipse JDT's cleanup infrastructure to support coordinated changes across multiple compilation units in a single atomic operation. This enables more powerful code transformations such as:

- Removing unused methods along with their interface declarations
- Migrating APIs across multiple files (e.g., JUnit 4 → 5)
- Modernizing code patterns that span multiple classes
- Removing dead code across an entire codebase

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

## Future Enhancements

Potential future improvements:
- UI for configuring multi-file cleanup preferences
- Extension point for third-party multi-file cleanups
- Performance optimizations for large codebases
- Integration with Save Actions

## See Also

- `ICleanUp` - Base cleanup interface
- `CleanUpContext` - Context passed to cleanups
- `CompositeChange` - LTK change aggregation
- `CleanUpRefactoring` - Cleanup orchestration
