package generator;

import com.google.common.collect.Table;
import java.util.*;

public class State {
    private Table<Integer, Integer, String> input;
    private Table<Integer, Integer, String> output;
    private LinkedHashMap<String, Table<Integer, Integer, String>> labOutputs;
    private Table<Integer, Integer, String> state;
    private Table<Integer, Integer, String> data;
    private Table<Integer, Integer, String> dyn;
    private int processPeriod;
    private int labPeriod;
    private int pulpeyePeriod;
    private int qcsPeriod;
    private int finalRow;
    private int numInputs;
    private int numOutputs;
    private int numState;
    private int lastInputCol;
    private int firstVal;
    private int dynRow;
    private double trim;
    private double draw;

    public State(
            Table<Integer, Integer, String> input,
            Table<Integer, Integer, String> output,
            LinkedHashMap<String, Table<Integer, Integer, String>> labOutputs,
            Table<Integer, Integer, String> state,
            Table<Integer, Integer, String> data,
            Table<Integer, Integer, String> dyn,
            int processPeriod,
            int labPeriod,
            int pulpeyePeriod,
            int qcsPeriod,
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
        this.pulpeyePeriod = pulpeyePeriod;
        this.qcsPeriod = qcsPeriod;
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

    // Helper methods moved from Generator
    private double calcNoise(double noise) {
        return 2 * Math.random() * noise - noise;
    }

    private int searchCol(String name, Table<Integer, Integer, String> t) {
        for (int i = 2; i < lastInputCol + 3; i++) {
            String var = t.get(1, i);
            if (var.equals(name))
                return i;
        }
        return 0;
    }

    /*
     * calcState: Method that applies specific calculations to some state variables
     */
    public void calcState(){
	System.out.println("calcState");
      stateSetup(searchCol("MV_SWFreeness", state), searchCol("MV_SWSpecificEnergy", data), searchCol("MV_SWFreeness", data));
      stateSetup(searchCol("MV_HWFreeness", state), searchCol("MV_HWSpecificEnergy", data), searchCol("MV_HWFreeness", data));
      stateSetup(searchCol("MV_OCCFreeness", state), searchCol("MV_OCCSpecificEnergy", data), searchCol("MV_OCCFreeness", data));
      for (int i = 3; i <= finalRow; i++){
         double wireSpeed = Double.parseDouble(data.get(i, searchCol("MV_WireSpeed", data)));
         if (wireSpeed <= 1){
            data.put(i, searchCol("MV_HeadboxPressure", data), "0");
            data.put(i, searchCol("MV_SliceOpening", data), "0.2");
            data.put(i, searchCol("MV_MachineSpeed", data), "0");
         }
         else {
            double jetVelocity = Double.parseDouble(data.get(i, searchCol("MV_JettoWire", data))) * wireSpeed;
            data.put(i, searchCol("MV_HeadboxPressure", data), String.valueOf(Math.pow(jetVelocity, 2) / (2 * 115920)));
            double sliceOpening = Double.parseDouble(data.get(i, searchCol("MV_ThinStockFlow", data))) * 12 / (7.48 * jetVelocity * trim);
            data.put(i, searchCol("MV_SliceOpening", data), String.valueOf(sliceOpening));
            data.put(i, searchCol("MV_MachineSpeed", data), String.valueOf(wireSpeed * draw));
         }

         double swFlow = Double.parseDouble(data.get(i, searchCol("MV_SWFlow", data)));
         double hwFlow = Double.parseDouble(data.get(i, searchCol("MV_HWFlow", data)));
         double occFlow = Double.parseDouble(data.get(i, searchCol("MV_OCCFlow", data)));
         double swCrill = Double.parseDouble(data.get(i, searchCol("PulpEye_SWCrill", data)));
         double hwCrill = Double.parseDouble(data.get(i, searchCol("PulpEye_HWCrill", data)));
         double occCrill = Double.parseDouble(data.get(i, searchCol("PulpEye_OCCCrill", data)));
         double totalFlow = swFlow + hwFlow + occFlow;
         double swFreeness = Double.parseDouble(data.get(i, searchCol("MV_SWFreeness", data)));
         double hwFreeness = Double.parseDouble(data.get(i, searchCol("MV_HWFreeness", data)));
         double occFreeness = Double.parseDouble(data.get(i, searchCol("MV_OCCFreeness", data)));
         if (totalFlow <= 100){
            data.put(i, searchCol("MV_SWPct", data), "0");
            data.put(i, searchCol("MV_HWPct", data), "0");
            data.put(i, searchCol("MV_OCCPct", data), "0");
            data.put(i, searchCol("PulpEye_BlendFreeness", data), "0");
            data.put(i, searchCol("PulpEye_BlendCrill", data), "0");
         }
         else {
            data.put(i, searchCol("MV_SWPct", data), String.valueOf(100 * swFlow / totalFlow));
            data.put(i, searchCol("MV_HWPct", data), String.valueOf(100 * hwFlow / totalFlow));
            data.put(i, searchCol("MV_OCCPct", data), String.valueOf(100 * occFlow / totalFlow));
            data.put(i, searchCol("PulpEye_BlendFreeness", data), String.valueOf((swFreeness * swFlow + hwFreeness * hwFlow + occFreeness * occFlow) / totalFlow));
            data.put(i, searchCol("PulpEye_BlendCrill", data), String.valueOf((swCrill * swFlow + hwCrill * hwFlow + occCrill * occFlow) / totalFlow));
         }
      }
   }

   /*
    * calcQCS: Method that calculates the QCS variable values
    */
   public void calcQCS(){
	System.out.println("calcQCS");
      // The input variables not required for the remaining methods have been written to a CSV file, therefore those input columns were cleared
      for (int i = 2; i < firstVal; i++){
         for (int j = 3; j < finalRow + 1; j++){
            data.put(j, i, "");
         }
      }

      int col = searchCol("QCS_Caliper", state);
      double caliperMax = Double.parseDouble(state.get(7,col));
      double caliperSlope = 0.02;
      double caliperNoise = Double.parseDouble(state.get(6,col));
      for (int i = 3; i <= finalRow; i++){
         double thinStockFlow;
         double thinStockConsistency;
         double pressLoad;
         double steamPressure;
         double machineSpeed;
         double blendFreeness;

         if (i > dynRow) {
            dynamicValues(i, searchCol("MV_ThinStockFlow", input), true);
            dynamicValues(i, searchCol("MV_ThinStockConsistency", input), true);
            dynamicValues(i, searchCol("MV_PressLoad", input), true);
            dynamicValues(i, searchCol("MV_SteamPressure", input), true);
            dynamicValues(i, searchCol("MV_MachineSpeed", state), false);
            dynamicValues(i, searchCol("PulpEye_BlendFreeness", state), false);
            thinStockFlow = Double.parseDouble(dyn.get(3, searchCol("MV_ThinStockFlow", dyn)));
            thinStockConsistency = Double.parseDouble(dyn.get(3, searchCol("MV_ThinStockConsistency", dyn)));
            pressLoad = Double.parseDouble(dyn.get(3, searchCol("MV_PressLoad", dyn)));
            steamPressure = Double.parseDouble(dyn.get(3, searchCol("MV_SteamPressure", dyn)));
            machineSpeed = Double.parseDouble(dyn.get(3, searchCol("MV_MachineSpeed", dyn)));
            blendFreeness = Double.parseDouble(dyn.get(3, searchCol("PulpEye_BlendFreeness", dyn)));
         }
         else{
            thinStockFlow = Double.parseDouble(data.get(i, searchCol("MV_ThinStockFlow", data)));
            thinStockConsistency = Double.parseDouble(data.get(i, searchCol("MV_ThinStockConsistency", data)));
            pressLoad = Double.parseDouble(data.get(i, searchCol("MV_PressLoad", data)));
            steamPressure = Double.parseDouble(data.get(i, searchCol("MV_SteamPressure", data)));
            machineSpeed = Double.parseDouble(data.get(i, searchCol("MV_MachineSpeed", data)));
		// 11/29/23 if freeness blank set to 0
		blendFreeness_str =data.get(i, searchCol("PulpEye_BlendFreeness", data));
		if (blendFreeness_str == "")	
		{
		blendFreeness = 0;
		}	
		else
		{
            	blendFreeness = Double.parseDouble(blendFreeness_str);
		}
         }

         double boneDryWeight;
         double fiberToHeadbox = thinStockFlow * thinStockConsistency * 8.3 / 100;
         double waterToHeadbox = thinStockFlow * 8.3 - fiberToHeadbox;
         double wireDrainage = 5 + 90 * (1 - 1 / Math.exp(blendFreeness));
         double waterToPress = waterToHeadbox * wireDrainage / 100;
         double pressDrainage = 80 * (1 - 1 / Math.exp(pressLoad / 200));
         double waterToDryers = waterToPress * pressDrainage / 100;
         double moistureToDryers = waterToDryers / fiberToHeadbox;
         double moistureAsymptote = 2.5 + machineSpeed / 500;
         data.put(i, searchCol("QCS_Moisture", data), String.valueOf(moistureAsymptote + (moistureToDryers - moistureAsymptote) / Math.exp(steamPressure / 25)));
         if (machineSpeed <= 1)
            boneDryWeight = 0;
         else
            boneDryWeight = fiberToHeadbox * 3300 / (machineSpeed * trim);
         data.put(i, searchCol("QCS_BoneDryWeight", data), String.valueOf(boneDryWeight));
         data.put(i, searchCol("QCS_BasisWeight", data), String.valueOf(boneDryWeight * (1 + Double.parseDouble(data.get(i, searchCol("QCS_Moisture", data))) / 100)));
         double capMaxCalc = caliperMax * boneDryWeight / 50;
         double capMinCalc = capMaxCalc / 2;
         double noise = calcNoise(caliperNoise);
         data.put(i, searchCol("QCS_Caliper", data), String.valueOf(capMinCalc + (capMaxCalc - capMinCalc) / Math.exp((pressLoad - 700) * caliperSlope) + noise));
      }
   }

   /*
    * calcLab: Method that calculates the output variables from the lab configurations
    */
   public void calcLab(){
       System.out.println("Starting calcLab...");
      List<Integer> inputNames = new ArrayList<>();
      calcList(inputNames, numInputs, input);
	System.out.println("calcLab Finished inputnames ...");

      List<Integer> stateNames = new ArrayList<>();
      calcList(stateNames, numState, state);
	System.out.println("calcLab Finished statenames ...");

      int lastLab = lastInputCol + numOutputs;
      int firstLab = lastInputCol + 1;
      int stateRow = numInputs + 2;
      // Since temporary dynamic values are being used, the method must go through every row so the dynamics can be calculated cumulatively
      labPeriod = labPeriod / processPeriod;
      for (int i = firstLab; i < lastLab + 1; i++){
         String name = data.get(1, i);
         int numRows = labPeriod;
         for (int j = 3; j <= finalRow; j ++){
            if (j > dynRow) {
               for (int input : inputNames) {
                  dynamicValues(j, input, true);
               }
               for (int state : stateNames) {
                  dynamicValues(j, state, false);
               }
            }
            if ((j - 3) % numRows == 0)
               data.put(j, i, String.valueOf(gainModel(name, stateRow, j)));
         }
      }
   }
} 