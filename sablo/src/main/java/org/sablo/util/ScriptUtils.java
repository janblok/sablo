package org.sablo.util;

public class ScriptUtils {
    /**
     * Transform service names like testpackage-myTestService into testPackageMyTestService - as latter is how getServiceScope gets called (generated code) from service client js,
     * and former is how auto-add-watches code knows the name (from the WebObjectSpecification)...
     */
    public static String convertToJSName(String name)
    {
        // this should do the same as websocket.ts #scriptifyServiceNameIfNeeded()
        int index = name.indexOf('-');
        while (index != -1 && name.length() > index + 1)
        {
            name = name.substring(0, index) + Character.toUpperCase(name.charAt(index + 1)) + name.substring(index + 2);
            index = name.indexOf('-');
        }
        return name;
    }
}
