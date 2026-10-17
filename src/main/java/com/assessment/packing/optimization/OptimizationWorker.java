package com.assessment.packing.optimization;

import com.assessment.packing.experiment.*;
import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.*;
import com.assessment.packing.solver.*;
import com.assessment.packing.validation.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** One request at a time per process; coordinator is the only CSV writer. */
public final class OptimizationWorker {
    private OptimizationWorker() {}
    public static Map<String,Object> execute(JsonNode request) throws Exception {
        long start=System.nanoTime();
        OptimizationConfiguration config=new OptimizationConfiguration(request.path("configuration"));
        long seed=request.path("seed").asLong();
        Instance instance=new InstanceReader().read(Path.of(request.path("instancePath").asText()));
        PackingAlgorithm solver=config.create(seed);
        Solution solution=solver.solve(instance);
        ValidationResult valid=new SolutionValidator().validate(instance,solution,config.rotationAllowed());
        long placementArea=solution.placements().stream().mapToLong(p->(long)p.width()*p.height()).sum();
        if(placementArea!=solution.packedArea())throw new IllegalStateException("placement area mismatch");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("runId",request.path("runId").asText()); result.put("configurationId",request.path("configurationId").asText());
        result.put("phase",request.path("phase").asText()); result.put("seed",seed); result.put("repeat",request.path("repeat").asInt(0));
        result.put("timestamp",Instant.now().toString()); result.put("instance",instance.name());
        result.put("configuration",config.json());result.put("status",valid.valid()?"COMPLETED":"INVALID"); result.put("valid",valid.valid());
        result.put("validationErrors",valid.errors());result.put("packedArea",solution.packedArea());result.put("sheetArea",instance.binArea());
        result.put("placementArea",placementArea);result.put("utilization",solution.utilization(instance));
        result.put("elapsedNanos",solution.elapsedNanos());result.put("totalElapsedNanos",System.nanoTime()-start);
        result.put("evaluations",evaluations(solver)); result.put("iterations",iterations(solver));
        result.put("evaluationSemantics",solver instanceof TabuSearchSolver?"evaluated_neighbors_including_cache_hits_excluding_initial_final":solver instanceof SimulatedAnnealingSolver?"temperature_samples_plus_neighbors_including_cache_hits_excluding_initial_final":config.stochastic()?"fitness_cache_misses_excluding_final_layout":"constructive_calls");
        Object stats=statistics(solver);result.put("statistics",stats);
        result.put("trace",solver instanceof ConvergenceTraceProvider t?t.lastConvergenceTrace():List.of(new ConvergencePoint(0,evaluations(solver),solution.packedArea())));
        List<Map<String,Object>> placements=new ArrayList<>();
        for(Placement p:solution.placements()) placements.add(Map.of("itemId",p.item().id(),"x",p.x(),"y",p.y(),"rotated",p.rotated(),"width",p.width(),"height",p.height()));
        result.put("placements",placements);
        return result;
    }
    private static Object statistics(PackingAlgorithm s){
        if(s instanceof TabuSearchSolver x)return x.lastStatistics();if(s instanceof SimulatedAnnealingSolver x)return x.lastStatistics();
        if(s instanceof GeneticAlgorithmSolver x)return Map.of("ga",x.lastStatistics(),"hybrid",x.lastHybridStatistics(),"adaptive",x.lastAdaptiveStatistics());
        if(s instanceof AntColonySolver x)return x.lastStatistics();if(s instanceof IteratedLocalSearchSolver x)return x.lastStatistics();
        if(s instanceof MaxRectsBestSolver x)return x.lastStatistics();return Map.of();
    }
    private static long evaluations(PackingAlgorithm s){
        if(s instanceof TabuSearchSolver x)return x.lastStatistics().evaluatedNeighbors();
        if(s instanceof SimulatedAnnealingSolver x)return x.lastStatistics().temperatureSampleEvaluations()+x.lastStatistics().searchNeighborEvaluations();
        if(s instanceof GeneticAlgorithmSolver x)return x.lastStatistics().fitnessEvaluations();
        if(s instanceof AntColonySolver x)return x.lastStatistics().fitnessEvaluations();
        if(s instanceof IteratedLocalSearchSolver x)return x.lastStatistics().fitnessEvaluations();
        if(s instanceof MaxRectsBestSolver x)return x.lastStatistics().entries().size();return 1;
    }
    private static long iterations(PackingAlgorithm s){
        if(s instanceof TabuSearchSolver x)return x.lastStatistics().completedIterations();
        if(s instanceof SimulatedAnnealingSolver x)return x.lastStatistics().completedTemperatureLevels();
        if(s instanceof GeneticAlgorithmSolver x)return x.lastStatistics().completedGenerations();
        if(s instanceof AntColonySolver x)return x.lastStatistics().completedIterations();
        if(s instanceof IteratedLocalSearchSolver x)return x.lastStatistics().completedIterations();return 1;
    }
    public static ValidationResult revalidate(JsonNode result,Instance instance){
        Map<Integer,Item> items=new HashMap<>();for(Item item:instance.items())items.put(item.id(),item);
        List<Placement> placements=new ArrayList<>();List<String> errors=new ArrayList<>();
        for(JsonNode p:result.path("placements")){
            Item item=items.get(p.path("itemId").asInt());if(item==null){errors.add("unknown exported item");continue;}
            Placement placement=new Placement(item,p.path("x").asInt(),p.path("y").asInt(),p.path("rotated").asBoolean());
            if(placement.width()!=p.path("width").asInt()||placement.height()!=p.path("height").asInt())errors.add("exported dimensions mismatch");
            placements.add(placement);
        }
        Solution restored=new Solution("exported",placements,0);
        errors.addAll(new SolutionValidator().validate(instance,restored,result.path("configuration").path("rotationAllowed").asBoolean()).errors());
        if(restored.packedArea()!=result.path("packedArea").asLong())errors.add("exported area mismatch");
        return new ValidationResult(errors.isEmpty(),errors);
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&args[0].equals("--validate")){
            JsonNode result=OptimizationConfiguration.JSON.readTree(Path.of(args[1]).toFile());
            ValidationResult valid=revalidate(result,new InstanceReader().read(Path.of(args[2])));
            System.out.println(OptimizationConfiguration.JSON.writeValueAsString(valid));if(!valid.valid())System.exit(2);return;
        }
        try(BufferedReader input=new BufferedReader(new InputStreamReader(System.in,java.nio.charset.StandardCharsets.UTF_8))){
            String line;
            while((line=input.readLine())!=null){
                JsonNode request=OptimizationConfiguration.JSON.readTree(line);Path target=Path.of(request.path("outputFile").asText());
                if(Files.exists(target)){
                    JsonNode previous=OptimizationConfiguration.JSON.readTree(target.toFile());
                    if(!previous.path("runId").equals(request.path("runId"))||!previous.path("configurationId").equals(request.path("configurationId"))||previous.path("seed").asLong()!=request.path("seed").asLong())throw new IllegalStateException("result identity mismatch");
                    System.out.println(target);System.out.flush();continue;
                }
                Map<String,Object> result;
                try{ result=execute(request); }
                catch(Exception failure){result=new LinkedHashMap<>();result.put("runId",request.path("runId").asText());result.put("configurationId",request.path("configurationId").asText());result.put("seed",request.path("seed").asLong());result.put("status","FAILED");result.put("valid",false);result.put("failure",failure.toString());result.put("timestamp",Instant.now().toString());}
                Files.createDirectories(target.getParent());Path temp=target.resolveSibling(target.getFileName()+".tmp");
                OptimizationConfiguration.JSON.writeValue(temp.toFile(),result);
                Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);
                System.out.println(target);System.out.flush();
            }
        }
    }
}
