package generator;

public class ProcessValues {
    public final double thinStockFlow;
    public final double thinStockConsistency;
    public final double pressLoad;
    public final double steamPressure;
    public final double machineSpeed;
    public final double blendFreeness;

    public ProcessValues(double thinStockFlow, double thinStockConsistency, 
                        double pressLoad, double steamPressure, 
                        double machineSpeed, double blendFreeness) {
        this.thinStockFlow = thinStockFlow;
        this.thinStockConsistency = thinStockConsistency;
        this.pressLoad = pressLoad;
        this.steamPressure = steamPressure;
        this.machineSpeed = machineSpeed;
        this.blendFreeness = blendFreeness;
    }
} 