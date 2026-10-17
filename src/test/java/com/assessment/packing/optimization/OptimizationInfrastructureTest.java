package com.assessment.packing.optimization;

import com.assessment.packing.experiment.*;
import com.assessment.packing.model.*;
import com.assessment.packing.solver.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationInfrastructureTest {
    static ObjectNode config(String algorithm,String decoder,String variant){
        ObjectNode n=OptimizationConfiguration.JSON.createObjectNode();n.put("algorithm",algorithm);n.put("profile","QUICK");n.put("order","AREA_DESC");n.put("rotationAllowed",true);n.put("decoder",decoder);n.put("decoderVariant",variant);n.put("budgetValue",120);n.put("mode","SOLVER");n.putObject("parameters");return n;
    }
    static String layout(Solution s){return s.placements().toString();}
    @Test void nativeAdapterMatchesEveryExistingFactorySearchEntry(){
        Instance ins=new Instance("sample",12,10,List.of(new Item(1,8,5),new Item(2,4,7),new Item(3,5,3),new Item(4,3,3)));
        for(AlgorithmId id:AlgorithmId.values())if(id.stochastic()){
            Solution original=SolverFactory.create(id,true,ItemOrder.AREA_DESC,42,ExperimentProfile.QUICK,SearchLimit.evaluations(120),PackingDecoderType.SKYLINE).solve(ins);
            Solution adapted=new OptimizationConfiguration(config(id.name(),"NATIVE","SKYLINE")).create(42).solve(ins);
            assertEquals(original.placements(),adapted.placements(),id.name());
        }
    }
    @Test void strictParsingRejectsUnknownInvalidAndInactiveParameters(){
        ObjectNode n=config("SA","NATIVE","SKYLINE");n.put("invented",1);assertThrows(IllegalArgumentException.class,()->new OptimizationConfiguration(n));n.remove("invented");
        ((ObjectNode)n.path("parameters")).putObject("ga").put("populationSize",30);assertThrows(IllegalArgumentException.class,()->new OptimizationConfiguration(n).create(1));
        ((ObjectNode)n.path("parameters")).removeAll();((ObjectNode)n.path("parameters")).putObject("sa").put("coolingRate",2);assertThrows(IllegalArgumentException.class,()->new OptimizationConfiguration(n).create(1));
        ((ObjectNode)n.path("parameters").path("sa")).removeAll().put("maxTemperatureLevels",1.5);assertThrows(IllegalArgumentException.class,()->new OptimizationConfiguration(n).create(1));
        assertThrows(IllegalArgumentException.class,()->new OptimizationConfiguration(config("SA","GUILLOTINE","BSSF:SAS:perhaps")));
    }
    @Test void existingVariantAdaptersMatchKernelsAndAreaPaths(){
        Instance ins=new Instance("sample",12,10,List.of(new Item(1,8,5),new Item(2,4,7),new Item(3,5,3),new Item(4,3,3)));
        List<Item> priority=ins.items();
        for(GuillotineRectChoice choice:GuillotineRectChoice.values())for(GuillotineSplitRule split:GuillotineSplitRule.values())for(boolean merge:List.of(false,true)){
            PackingDecoder d=new OptimizationConfiguration(config("SA","GUILLOTINE",choice+":"+split+":"+merge)).decoder();
            var expected=new GuillotineSolver(true,ItemOrder.INPUT,choice,split,merge,false).solve(ins);
            assertEquals(expected.placements(),d.solveInPriorityOrder(ins,priority).placements());assertEquals(expected.packedArea(),d.packedAreaInPriorityOrder(ins,priority));
        }
        for(ShelfHeuristic h:ShelfHeuristic.values())for(boolean wm:List.of(false,true)){
            PackingDecoder d=new OptimizationConfiguration(config("SA","SHELF",h+":"+wm)).decoder();
            assertEquals(new ShelfSolver(true,ItemOrder.INPUT,h,wm).solve(ins).placements(),d.solveInPriorityOrder(ins,priority).placements());
            assertEquals(d.solveInPriorityOrder(ins,priority).packedArea(),d.packedAreaInPriorityOrder(ins,priority));
        }
        for(SkylinePlacementHeuristic h:SkylinePlacementHeuristic.values())for(boolean wm:List.of(false,true)){
            PackingDecoder d=new OptimizationConfiguration(config("SA","SKYLINE",h+":"+wm)).decoder();assertEquals(new SkylineGlobalSolver(true,ItemOrder.INPUT,h,wm,SkylineSelectionMode.SEQUENTIAL).solve(ins).placements(),d.solveInPriorityOrder(ins,priority).placements());
            assertEquals(d.solveInPriorityOrder(ins,priority).packedArea(),d.packedAreaInPriorityOrder(ins,priority));
        }
        for(MaxRectsHeuristic h:MaxRectsHeuristic.values()){
            PackingDecoder d=new OptimizationConfiguration(config("SA","MAXRECTS",h.name())).decoder();assertEquals(new MaxRectsSolver(true,ItemOrder.INPUT,h,false).solve(ins).placements(),d.solveInPriorityOrder(ins,priority).placements());
            assertEquals(d.solveInPriorityOrder(ins,priority).packedArea(),d.packedAreaInPriorityOrder(ins,priority));
        }
    }
    @Test void variantAndParameterActuallyChangeExecutionOnMinimalWitness(){
        Random random=new Random(1234);boolean witness=false;
        for(int attempt=0;attempt<200&&!witness;attempt++){
            List<Item> items=new ArrayList<>();for(int i=1;i<=6;i++)items.add(new Item(i,1+random.nextInt(9),1+random.nextInt(9)));
            Instance ins=new Instance("witness",10,10,items);
            var a=new OptimizationConfiguration(config("SA","SHELF","NEXT_FIT:false")).decoder().solveInPriorityOrder(ins,items);
            var b=new OptimizationConfiguration(config("SA","SHELF","BEST_AREA_FIT:false")).decoder().solveInPriorityOrder(ins,items);
            witness=!a.placements().equals(b.placements());
        }
        assertTrue(witness,"six-item witness must distinguish actual variant execution");
        ObjectNode n=config("SA","NATIVE","SKYLINE");((ObjectNode)n.path("parameters")).putObject("sa").put("maxTemperatureLevels",1).put("attemptsPerTemperature",1).put("initialTemperatureSamples",1);
        Instance ins=new Instance("n",10,10,List.of(new Item(1,6,6),new Item(2,7,7),new Item(3,8,8)));
        var solver=(SimulatedAnnealingSolver)new OptimizationConfiguration(n).create(5);solver.solve(ins);assertEquals(1,solver.lastStatistics().completedTemperatureLevels());
        assertEquals(n,new OptimizationConfiguration(n).json());
    }
    @Test void independentConcurrentSolversMatchSerialResults()throws Exception{
        Instance ins=new Instance("test",20,20,List.of(new Item(1,12,12),new Item(2,8,12),new Item(3,7,7),new Item(4,9,5)));
        ObjectNode n=config("ADAPTIVE_GA","MAXRECTS","BSSF");var expected=new OptimizationConfiguration(n).create(99).solve(ins).placements();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->new OptimizationConfiguration(n).create(99).solve(ins).placements());var b=pool.submit(()->new OptimizationConfiguration(n).create(99).solve(ins).placements());assertEquals(expected,a.get());assertEquals(expected,b.get());}finally{pool.shutdownNow();}
    }
    @Test void exportedLayoutIsIndependentlyValidatedAndTamperingRejected(){
        Instance ins=new Instance("test",10,10,List.of(new Item(1,6,6)));
        ObjectNode n=OptimizationConfiguration.JSON.createObjectNode();n.set("configuration",config("SA","NATIVE","SKYLINE"));n.put("packedArea",36);
        var p=n.putArray("placements").addObject();p.put("itemId",1);p.put("x",0);p.put("y",0);p.put("width",6);p.put("height",6);p.put("rotated",false);
        assertTrue(OptimizationWorker.revalidate(n,ins).valid());p.put("x",6);assertFalse(OptimizationWorker.revalidate(n,ins).valid());p.put("x",0);n.put("packedArea",37);assertFalse(OptimizationWorker.revalidate(n,ins).valid());
    }
}
