package glide.api.utils;

import glide.api.models.GlideString;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class for converting between String and GlideString (binary) representations.
 * This class provides common conversion methods to be reused across different commands.
 */
public class BinaryConversionUtils {

    /**
     * Convert a Map<String, Map<String, String[][]>> to Map<GlideString, Map<GlideString, GlideString[][]>>.
     * Used for XREAD/XREADGROUP binary responses.
     * 
     * @param stringResult The string-based result
     * @return The binary-based result
     */
    public static Map<GlideString, Map<GlideString, GlideString[][]>> convertXReadResultToBinary(
            Map<String, Map<String, String[][]>> stringResult) {
        if (stringResult == null) {
            return null;
        }
        
        Map<GlideString, Map<GlideString, GlideString[][]>> binaryResult = new LinkedHashMap<>();
        
        for (Map.Entry<String, Map<String, String[][]>> streamEntry : stringResult.entrySet()) {
            Map<GlideString, GlideString[][]> binaryStreamData = new LinkedHashMap<>();
            
            for (Map.Entry<String, String[][]> messageEntry : streamEntry.getValue().entrySet()) {
                String[][] fields = messageEntry.getValue();
                GlideString[][] binaryFields = new GlideString[fields.length][2];
                
                for (int i = 0; i < fields.length; i++) {
                    binaryFields[i][0] = GlideString.of(fields[i][0]);
                    binaryFields[i][1] = GlideString.of(fields[i][1]);
                }
                
                binaryStreamData.put(GlideString.of(messageEntry.getKey()), binaryFields);
            }
            
            binaryResult.put(GlideString.of(streamEntry.getKey()), binaryStreamData);
        }
        
        return binaryResult;
    }

    /**
     * Convert a Map<String, String[]> to Map<GlideString, GlideString[]>.
     * Used for various hash and stream command responses.
     * 
     * @param stringResult The string-based result
     * @return The binary-based result
     */
    public static Map<GlideString, GlideString[]> convertMapStringArrayToBinary(
            Map<String, String[]> stringResult) {
        if (stringResult == null) {
            return null;
        }
        
        Map<GlideString, GlideString[]> binaryResult = new LinkedHashMap<>();
        
        for (Map.Entry<String, String[]> entry : stringResult.entrySet()) {
            String[] values = entry.getValue();
            GlideString[] binaryValues = new GlideString[values.length];
            
            for (int i = 0; i < values.length; i++) {
                binaryValues[i] = GlideString.of(values[i]);
            }
            
            binaryResult.put(GlideString.of(entry.getKey()), binaryValues);
        }
        
        return binaryResult;
    }

    /**
     * Convert a Map<String, String> to Map<GlideString, GlideString>.
     * Used for various command responses that return key-value pairs.
     * 
     * @param stringResult The string-based result
     * @return The binary-based result
     */
    public static Map<GlideString, GlideString> convertMapStringToBinary(
            Map<String, String> stringResult) {
        if (stringResult == null) {
            return null;
        }
        
        Map<GlideString, GlideString> binaryResult = new LinkedHashMap<>();
        
        for (Map.Entry<String, String> entry : stringResult.entrySet()) {
            binaryResult.put(GlideString.of(entry.getKey()), GlideString.of(entry.getValue()));
        }
        
        return binaryResult;
    }

    /**
     * Convert a Map<String, Double> to Map<GlideString, Double>.
     * Used for sorted set commands with scores.
     * 
     * @param stringResult The string-based result
     * @return The binary-based result
     */
    public static Map<GlideString, Double> convertMapStringDoubleToBinary(
            Map<String, Double> stringResult) {
        if (stringResult == null) {
            return null;
        }
        
        Map<GlideString, Double> binaryResult = new LinkedHashMap<>();
        
        for (Map.Entry<String, Double> entry : stringResult.entrySet()) {
            binaryResult.put(GlideString.of(entry.getKey()), entry.getValue());
        }
        
        return binaryResult;
    }

    /**
     * Convert a String[] to GlideString[].
     * Used for commands that return arrays of strings.
     * 
     * @param stringArray The string array
     * @return The binary array
     */
    public static GlideString[] convertStringArrayToBinary(String[] stringArray) {
        if (stringArray == null) {
            return null;
        }
        
        GlideString[] binaryArray = new GlideString[stringArray.length];
        for (int i = 0; i < stringArray.length; i++) {
            binaryArray[i] = stringArray[i] == null ? null : GlideString.of(stringArray[i]);
        }
        
        return binaryArray;
    }

    /**
     * Convert a String[][] to GlideString[][].
     * Used for commands that return 2D arrays.
     * 
     * @param stringArray The 2D string array
     * @return The 2D binary array
     */
    public static GlideString[][] convertString2DArrayToBinary(String[][] stringArray) {
        if (stringArray == null) {
            return null;
        }
        
        GlideString[][] binaryArray = new GlideString[stringArray.length][];
        for (int i = 0; i < stringArray.length; i++) {
            if (stringArray[i] != null) {
                binaryArray[i] = convertStringArrayToBinary(stringArray[i]);
            }
        }
        
        return binaryArray;
    }

    /**
     * Convert Object[] where each element is a Map.Entry to Map<GlideString, GlideString>.
     * Used for commands that return arrays of key-value pairs.
     * 
     * @param entries The array of entries
     * @return The binary map
     */
    public static Map<GlideString, GlideString> convertEntryArrayToBinaryMap(Object[] entries) {
        if (entries == null) {
            return null;
        }
        
        Map<GlideString, GlideString> result = new LinkedHashMap<>();
        for (Object entry : entries) {
            if (entry instanceof Object[]) {
                Object[] pair = (Object[]) entry;
                if (pair.length >= 2) {
                    String key = pair[0] != null ? pair[0].toString() : null;
                    String value = pair[1] != null ? pair[1].toString() : null;
                    if (key != null) {
                        result.put(GlideString.of(key), value != null ? GlideString.of(value) : null);
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * Convert GlideString[] to String[].
     * Used for converting binary arguments to string arguments.
     * 
     * @param binaryArray The binary array
     * @return The string array
     */
    public static String[] convertBinaryArrayToString(GlideString[] binaryArray) {
        if (binaryArray == null) {
            return null;
        }
        
        String[] stringArray = new String[binaryArray.length];
        for (int i = 0; i < binaryArray.length; i++) {
            stringArray[i] = binaryArray[i] != null ? binaryArray[i].toString() : null;
        }
        
        return stringArray;
    }

    /**
     * Convert a Map<GlideString, GlideString> to Map<String, String>.
     * Used for converting binary maps to string maps.
     * 
     * @param binaryMap The binary map
     * @return The string map
     */
    public static Map<String, String> convertBinaryMapToString(Map<GlideString, GlideString> binaryMap) {
        if (binaryMap == null) {
            return null;
        }
        
        Map<String, String> stringMap = new HashMap<>();
        for (Map.Entry<GlideString, GlideString> entry : binaryMap.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().toString() : null;
            String value = entry.getValue() != null ? entry.getValue().toString() : null;
            if (key != null) {
                stringMap.put(key, value);
            }
        }
        
        return stringMap;
    }
}