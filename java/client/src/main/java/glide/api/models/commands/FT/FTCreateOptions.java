/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

/**
 * Stub implementation of FT.CREATE options for compilation compatibility.
 * This is a basic stub to allow tests to compile and will need full implementation.
 */
public class FTCreateOptions {
    
    // Basic stub - constructor  
    public FTCreateOptions() {
    }
    
    // Stub enum for DataType
    public enum DataType {
        TEXT,
        NUMERIC,
        GEO,
        TAG,
        VECTOR
    }
    
    // Stub enum for DistanceMetric
    public enum DistanceMetric {
        L2,
        IP,
        COSINE
    }
    
    // Stub base class for field information
    public static class FieldInfo {
        protected final String fieldName;
        protected final String alias;
        protected final Object fieldType;
        
        // Constructor for field name only
        public FieldInfo(String fieldName) {
            this.fieldName = fieldName;
            this.alias = null;
            this.fieldType = null;
        }
        
        // Constructor for field name and field type  
        public FieldInfo(String fieldName, Object fieldType) {
            this.fieldName = fieldName;
            this.alias = null;
            this.fieldType = fieldType;
        }
        
        // Constructor for field name, alias, and field type
        public FieldInfo(String fieldName, String alias, Object fieldType) {
            this.fieldName = fieldName;
            this.alias = alias;
            this.fieldType = fieldType;
        }
        
        public String getFieldName() {
            return fieldName;
        }
        
        public String getAlias() {
            return alias;
        }
        
        public Object getFieldType() {
            return fieldType;
        }
    }
    
    // Stub class for numeric fields
    public static class NumericField extends FieldInfo {
        public NumericField(String fieldName) {
            super(fieldName);
        }
        
        // Default constructor for NumericField
        public NumericField() {
            super("numeric");
        }
    }
    
    // Stub class for text fields
    public static class TextField extends FieldInfo {
        public TextField(String fieldName) {
            super(fieldName);
        }
        
        // Default constructor for TextField
        public TextField() {
            super("text");
        }
    }
    
    // Stub class for tag fields
    public static class TagField extends FieldInfo {
        public TagField(String fieldName) {
            super(fieldName);
        }
        
        // Constructor for TagField with no parameters
        public TagField() {
            super("tag");
        }
        
        // Constructor for TagField with separator character
        public TagField(char separator) {
            super("tag");
        }
    }
    
    // Stub class for vector fields using FLAT algorithm
    public static class VectorFieldFlat extends FieldInfo {
        public VectorFieldFlat(String fieldName) {
            super(fieldName);
        }
        
        // Builder method for VectorFieldFlat
        public static VectorFieldFlat builder(DistanceMetric metric, int dimensions) {
            return new VectorFieldFlat("temp"); // Stub implementation
        }
        
        // Build method for builder pattern
        public VectorFieldFlat build() {
            return this;
        }
    }
    
    // Stub class for vector fields using HNSW algorithm
    public static class VectorFieldHnsw extends FieldInfo {
        public VectorFieldHnsw(String fieldName) {
            super(fieldName);
        }
        
        // Builder method for VectorFieldHnsw
        public static VectorFieldHnsw builder(DistanceMetric metric, int dimensions) {
            return new VectorFieldHnsw("temp"); // Stub implementation
        }
        
        // Build method for builder pattern
        public VectorFieldHnsw build() {
            return this;
        }
    }
}