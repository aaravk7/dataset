package generator;

import com.google.common.collect.Table;
import java.util.*;

public class State {
    private final Table<Integer, Integer, String> input;
    private final Table<Integer, Integer, String> output;
    private final LinkedHashMap<String, Table<Integer, Integer, String>> labOutputs;
    private final Table<Integer, Integer, String> state;
    private final Table<Integer, Integer, String> data;
    private final Table<Integer, Integer, String> dyn;
    private final int processPeriod;
    private int labPeriod;
    private final int finalRow;
    private final int numInputs;
    private final int numOutputs;
    private final int numState;
    private final int lastInputCol;
    private final int firstVal;
    private final int dynRow;
    private final double trim;
    private final double draw;

    public State(
            Table<Integer, Integer, String> input,
            Table<Integer, Integer, String> output,
            LinkedHashMap<String, Table<Integer, Integer, String>> labOutputs,
            Table<Integer, Integer, String> state,
            Table<Integer, Integer, String> data,
            Table<Integer, Integer, String> dyn,
            int processPeriod,
            int labPeriod,
            int finalRow,
            int numInputs,
            int numOutputs,
            int numState,
            int lastInputCol,
            int firstVal,
            int dynRow,
            double trim,
            double draw) {
        
        this.input = input;
        this.output = output;
        this.labOutputs = labOutputs;
        this.state = state;
        this.data = data;
        this.dyn = dyn;
        this.processPeriod = processPeriod;
        this.labPeriod = labPeriod;
        this.finalRow = finalRow;
        this.numInputs = numInputs;
        this.numOutputs = numOutputs;
        this.numState = numState;
        this.lastInputCol = lastInputCol;
        this.firstVal = firstVal;
        this.dynRow = dynRow;
        this.trim = trim;
        this.draw = draw;
    }

    private double calcNoise(double noise) {
        return 2 * Math.random() * noise - noise;
    }

    private int searchCol(String name, Table<Integer, Integer, String> t) {
        for (int i = 2; i < lastInputCol + 3; i++) {
            String var = t.get(1, i);
            if (var != null && var.equals(name)) {
                return i;
            }
        }
        return 0;
    }

    private void stateSetup(int col, int inCol, int stateCol) {
        double intercept = 1000;
        double asymptote = 300;
        double slope = 0.5;
        double noise = Double.parseDouble(state.get(6, col));
        
        for (int i = 3; i <= finalRow; i++) {
            double noiseVal = calcNoise(noise);
            double inputVal = Double.parseDouble(data.get(i, inCol));
            double val = intercept - (intercept - asymptote) * (1 - 1 / Math.exp(slope * inputVal)) + noiseVal;
            data.put(i, stateCol, String.valueOf(val));
        }
    }

    public void calcState() {
        // Calculate freeness for different materials
        stateSetup(searchCol("MV_SWFreeness", state), 
                  searchCol("MV_SWSpecificEnergy", data), 
                  searchCol("MV_SWFreeness", data));
        
        stateSetup(searchCol("MV_HWFreeness", state), 
                  searchCol("MV_HWSpecificEnergy", data), 
                  searchCol("MV_HWFreeness", data));
        
        stateSetup(searchCol("MV_OCCFreeness", state), 
                  searchCol("MV_OCCSpecificEnergy", data), 
                  searchCol("MV_OCCFreeness", data));

        // Process each row of data
        for (int i = 3; i <= finalRow; i++) {
            double wireSpeed = getDoubleValue(i, "MV_WireSpeed");
            
            if (wireSpeed <= 1) {
                data.put(i, searchCol("MV_HeadboxPressure", data), "0");
                data.put(i, searchCol("MV_SliceOpening", data), "0.2");
                data.put(i, searchCol("MV_MachineSpeed", data), "0");
            } else {
                double jetVelocity = getDoubleValue(i, "MV_JettoWire") * wireSpeed;
                data.put(i, searchCol("MV_HeadboxPressure", data), 
                        String.valueOf(Math.pow(jetVelocity, 2) / (2 * 115920)));
                data.put(i, searchCol("MV_SliceOpening", data), 
                        String.valueOf(getDoubleValue(i, "MV_ThinStockFlow") * 12 / (7.48 * jetVelocity * trim)));
                data.put(i, searchCol("MV_MachineSpeed", data), 
                        String.valueOf(wireSpeed * draw));
            }

            calculateBlendValues(i);
        }
    }

    public void calcQCS() {
        // Clear unused input columns
        for (int i = 2; i < firstVal; i++) {
            for (int j = 3; j < finalRow + 1; j++) {
                data.put(j, i, "");
            }
        }

        int caliperCol = searchCol("QCS_Caliper", state);
        double caliperMax = Double.parseDouble(state.get(7, caliperCol));
        double caliperSlope = 0.02;
        double caliperNoise = Double.parseDouble(state.get(6, caliperCol));

        for (int i = 3; i <= finalRow; i++) {
            ProcessValues values = getProcessValues(i);
            calculateQCSValues(i, values, caliperMax, caliperSlope, caliperNoise);
        }
    }

    public void calcLab() {
        List<Integer> inputNames = new ArrayList<>();
        List<Integer> stateNames = new ArrayList<>();
        
        calcList(inputNames, numInputs, input);
        calcList(stateNames, numState, state);

        int lastLab = lastInputCol + numOutputs;
        int firstLab = lastInputCol + 1;
        int stateRow = numInputs + 2;
        
        labPeriod = labPeriod / processPeriod;

        for (int i = firstLab; i < lastLab + 1; i++) {
            String name = data.get(1, i);
            int numRows = labPeriod;
            
            for (int j = 3; j <= finalRow; j++) {
                if (j > dynRow) {
                    for (int inputCol : inputNames) {
                        dynamicValues(j, inputCol, true);
                    }
                    for (int stateCol : stateNames) {
                        dynamicValues(j, stateCol, false);
                    }
                }
                if ((j - 3) % numRows == 0) {
                    data.put(j, i, String.valueOf(gainModel(name, stateRow, j)));
                }
            }
        }
    }

    private void calculateBlendValues(int row) {
        double swFlow = getDoubleValue(row, "MV_SWFlow");
        double hwFlow = getDoubleValue(row, "MV_HWFlow");
        double occFlow = getDoubleValue(row, "MV_OCCFlow");
        double totalFlow = swFlow + hwFlow + occFlow;

        if (totalFlow <= 100) {
            data.put(row, searchCol("MV_SWPct", data), "0");
            data.put(row, searchCol("MV_HWPct", data), "0");
            data.put(row, searchCol("MV_OCCPct", data), "0");
            data.put(row, searchCol("PulpEye_BlendFreeness", data), "0");
            data.put(row, searchCol("PulpEye_BlendCrill", data), "0");
        } else {
            data.put(row, searchCol("MV_SWPct", data), String.valueOf(100 * swFlow / totalFlow));
            data.put(row, searchCol("MV_HWPct", data), String.valueOf(100 * hwFlow / totalFlow));
            data.put(row, searchCol("MV_OCCPct", data), String.valueOf(100 * occFlow / totalFlow));
            
            double blendFreeness = (getDoubleValue(row, "MV_SWFreeness") * swFlow + 
                                  getDoubleValue(row, "MV_HWFreeness") * hwFlow + 
                                  getDoubleValue(row, "MV_OCCFreeness") * occFlow) / totalFlow;
            
            double blendCrill = (getDoubleValue(row, "PulpEye_SWCrill") * swFlow + 
                               getDoubleValue(row, "PulpEye_HWCrill") * hwFlow + 
                               getDoubleValue(row, "PulpEye_OCCCrill") * occFlow) / totalFlow;
            
            data.put(row, searchCol("PulpEye_BlendFreeness", data), String.valueOf(blendFreeness));
            data.put(row, searchCol("PulpEye_BlendCrill", data), String.valueOf(blendCrill));
        }
    }

    private double getDoubleValue(int row, String columnName) {
        String value = data.get(row, searchCol(columnName, data));
        return (value == null || value.isEmpty()) ? 0.0 : Double.parseDouble(value);
    }

    private void calcList(List<Integer> list, int size, Table<Integer, Integer, String> table) {
        for (String i : labOutputs.keySet()) {
            for (int j = 2; j <= labOutputs.get(i).rowKeySet().size(); j++) {
                for (int c = 2; c <= size + 1; c++) {
                    if (labOutputs.get(i).get(j, 1).equals(table.get(1, c)) && !list.contains(c)) {
                        list.add(c);
                    }
                }
            }
        }
    }

    private void dynamicValues(int row, int col, boolean isInput) {
        Table<Integer, Integer, String> table = isInput ? input : state;
        int deadTime = (int) (Double.parseDouble(table.get(3, col)) * 60 / processPeriod);
        double lag1 = Double.parseDouble(table.get(4, col));
        double lag2 = Double.parseDouble(table.get(5, col));
        
        if (!isInput) {
            col += numInputs;
        }

        lag1 = calculateLag(lag1);
        lag2 = calculateLag(lag2);

        double inputDeadtime = Double.parseDouble(data.get(row - deadTime, col));
        double inLag1, inLag2;

        if (row == dynRow + 1) {
            inLag1 = Double.parseDouble(data.get(row - deadTime - 1, col));
            inLag2 = Double.parseDouble(data.get(row - deadTime - 2, col));
        } else {
            inLag1 = Double.parseDouble(dyn.get(3, col));
            inLag2 = Double.parseDouble(dyn.get(4, col));
        }

        double result = lag2 <= 0 ? 
            inputDeadtime * lag1 + inLag1 * (1 - lag1) : 
            secondOrder(inputDeadtime, inLag1, inLag2, lag1, lag2);

        dyn.put(2, col, String.valueOf(inputDeadtime));
        dyn.put(3, col, String.valueOf(result));
        dyn.put(4, col, String.valueOf(inLag1));
    }

    private double calculateLag(double lag) {
        if (lag <= 0) return 1;
        double filterVal = 0.63 / (lag * 60 / processPeriod);
        return filterVal > 1 ? 1 : filterVal;
    }

    private double secondOrder(double newOut, double out1, double out2, double lag1, double lag2) {
        double firstPrior = (out1 - out2 * (1 - lag2)) / lag2;
        double firstCurrent = newOut * lag1 + firstPrior * (1 - lag1);
        return firstCurrent * lag2 + out1 * (1 - lag2);
    }
} 