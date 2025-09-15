# ResourcesRouteV2E2ESpec Refactoring Analysis

## Code Duplication Patterns Identified

### 1. HTTP Request Patterns (90 TestApiClient calls)
- Pattern: TestApiClient.get/post → assert200 → response processing
- Repeated across different endpoints and schemas
- Opportunity: Extract common HTTP assertion helper

### 2. File Reading and Comparison (40 readFile calls)
- Pattern: readFile(expectedFile) → comparison with response  
- Used for JSON-LD (23x), Turtle (4x), RDF/XML (4x)
- Opportunity: Create generic file comparison helper

### 3. Schema Testing Repetition
- Same test logic repeated for different schemas (simple, complex)
- Same resource tested with JSON-LD, Turtle, RDF/XML formats
- Pattern: Get resource → Compare with expected file → Validate

### 4. Resource Request Variations
- Resource vs Preview requests follow identical patterns
- Different HTTP headers but same validation logic
- URL parameter vs header parameter variations

## Refactoring Recommendations

### 1. Create Helper Methods
```scala
private def testResourceWithSchema(
  resourceIri: String,
  schema: String,
  expectedFile: String,
  mediaType: String = "application/ld+json"
): ZIO[Any, Throwable, TestResult]

private def httpGetAndAssert[T](
  uri: Uri,
  headers: Map[String, String] = Map.empty
)(processor: String => ZIO[Any, Throwable, T]): ZIO[Any, Throwable, T]
```

### 2. Parameterized Tests
- Create test suite for format variations (JSON-LD, Turtle, RDF/XML)
- Use ZIO Test's property-based testing for schema combinations

### 3. Extract Common Patterns
- File comparison utilities
- RDF model validation
- Instance checker validation

## Impact Analysis
- **Current**: 27,929 tokens, high duplication
- **After refactoring**: Estimated 40% reduction in code size
- **Benefits**: Easier maintenance, consistent test patterns, reduced errors

## Implementation Priority
1. Extract HTTP helpers (immediate impact)
2. Create schema testing utilities
3. Implement parameterized tests
4. Consolidate file operations