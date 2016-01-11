package org.project.openbaton.cli.model;

import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.project.openbaton.cli.util.PrintFormat.addRow;
import static org.project.openbaton.cli.util.PrintFormat.buildLine;
import static org.project.openbaton.cli.util.PrintFormat.printer;


/**
 * Created by tce on 25.09.15.
 */
public class GeneralTarget {

    public static String Print(List<Object> object) throws InvocationTargetException, IllegalAccessException {
        String result = "";

        String firstline = "";
        String secondline = "";
        String line1 = "";
        String[] rowproperty = new String[500];
        String[] rowvalue = new String[500];
        String[] row1 = new String[500];

        int rowcount = 0;

        Field[] fieldBase = object.get(0).getClass().getDeclaredFields();
        Field[] fieldSuper = object.get(0).getClass().getSuperclass().getDeclaredFields();
        Field[] field = ArrayUtils.addAll(fieldBase, fieldSuper);

        rowproperty[rowcount] = "| ID";
        rowvalue[rowcount] = "| VERSION";
        row1[rowcount] = "| TARGET";
        rowcount++;

        for (int i = 0; i < object.size(); i++) {
            Method[] methodBase = object.get(i).getClass().getDeclaredMethods();
            Method[] methodSuper = object.get(i).getClass().getSuperclass().getDeclaredMethods();
            Method[] methods = ArrayUtils.addAll(methodBase, methodSuper);

            for (int z = 0; z < methods.length; z++) {

                if (methods[z].getName().equalsIgnoreCase("getID")) {
                    rowproperty[rowcount] = "| " + methods[z].invoke(object.get(i)).toString();
                }

                if (methods[z].getName().equalsIgnoreCase("getVersion")) {
                    rowvalue[rowcount] = "| " + methods[z].invoke(object.get(i)).toString();
                }

                if (methods[z].getName().equalsIgnoreCase("getTarget")) {
                    row1[rowcount] = "| " + methods[z].invoke(object.get(i)).toString();
                }

            }
            rowcount++;
        }


        addRow("\n");


        firstline = buildLine(rowproperty);
        secondline = buildLine(rowvalue);
        line1 = buildLine(row1);

        addRow(firstline, secondline, line1, "+");

        for (int c = 0; c < rowcount; c++) {
            addRow(rowproperty[c], rowvalue[c], row1[c], "|");
            if (c == 0) {
                addRow(firstline, secondline, line1, "+");
            }
        }

        addRow(firstline, secondline, line1, "+");


        result = printer();


        return result;
    }
}