package com.assessment.packing.optimization;

import com.assessment.packing.experiment.*;
import com.assessment.packing.model.*;
import com.assessment.packing.solver.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

/** Experiment-only adapter. All placement and search operators remain in existing solvers. */
public final class OptimizationConfiguration {
    public static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> KEYS = Set.of("algorithm", "profile", "order", "rotationAllowed",
            "decoder", "decoderVariant", "budgetValue", "parameters", "mode");
    private final ObjectNode value;

    public OptimizationConfiguration(JsonNode input) {
        if (!input.isObject()) throw new IllegalArgumentException("configuration must be an object");
        input.fieldNames().forEachRemaining(k -> { if (!KEYS.contains(k)) throw new IllegalArgumentException("unknown field: " + k); });
        value = ((ObjectNode) input).deepCopy();
        for (String k : List.of("algorithm", "profile", "order", "decoder", "decoderVariant", "mode")) {
            if (!input.hasNonNull(k) || !input.get(k).isTextual()) throw new IllegalArgumentException("missing text: " + k);
        }
        if (!input.path("rotationAllowed").isBoolean()) throw new IllegalArgumentException("rotationAllowed must be boolean");
        if (!input.path("budgetValue").isIntegralNumber() || !input.path("budgetValue").canConvertToLong()
                || input.path("budgetValue").asLong() < 0) throw new IllegalArgumentException("invalid budget");
        if (!input.path("parameters").isObject()) throw new IllegalArgumentException("parameters must be object");
        AlgorithmId.parse(input.path("algorithm").asText());
        ExperimentProfile.parse(input.path("profile").asText());
        ItemOrder.parse(input.path("order").asText());
        if (!Set.of("SOLVER", "DECODE_ONLY").contains(input.path("mode").asText())) throw new IllegalArgumentException("unknown mode");
        decoder(); // eagerly validate variant parsing
    }

    public JsonNode json() { return value.deepCopy(); }
    public boolean rotationAllowed() { return value.path("rotationAllowed").asBoolean(); }
    public boolean stochastic() { return value.path("mode").asText().equals("SOLVER") && AlgorithmId.parse(value.path("algorithm").asText()).stochastic(); }
    public PackingAlgorithm create(long seed) {
        AlgorithmId id = AlgorithmId.parse(value.path("algorithm").asText());
        boolean rot = rotationAllowed();
        ItemOrder order = ItemOrder.parse(value.path("order").asText());
        PackingDecoder decoder = decoder();
        if (value.path("mode").asText().equals("DECODE_ONLY")) {
            requireParameterGroups(Set.of());
            return new PackingAlgorithm() {
                public String name() { return "Fixed-order decoding(" + decoder.name() + "," + order + ")"; }
                public Solution solve(Instance instance) {
                    long start = System.nanoTime();
                    Solution s = decoder.solveInPriorityOrder(instance, ItemOrdering.sort(instance.items(), order));
                    return new Solution(name(), s.placements(), System.nanoTime() - start);
                }
            };
        }
        long budget = value.path("budgetValue").asLong();
        SearchLimit limit = budget == 0 ? SearchLimit.unlimited() : SearchLimit.evaluations(budget);
        ExperimentProfile profile = ExperimentProfile.parse(value.path("profile").asText());
        if (!id.stochastic()) {
            requireParameterGroups(Set.of());
            return SolverFactory.create(id, rot, order, seed, profile, limit);
        }
        boolean quick = profile == ExperimentProfile.QUICK;
        switch (id) {
            case TABU_FULL, TABU_MOVE, TABU_POSITION, TABU_ORDER, TABU_ADJACENCY, TABU_PLACEMENT -> {
                requireParameterGroups(Set.of("tabu"));
                TabuMode mode = switch (id) {
                    case TABU_FULL -> TabuMode.FULL_STATE; case TABU_MOVE -> TabuMode.MOVE;
                    case TABU_POSITION -> TabuMode.POSITION_ATTRIBUTE; case TABU_ORDER -> TabuMode.RELATIVE_ORDER_ATTRIBUTE;
                    case TABU_ADJACENCY -> TabuMode.ADJACENCY_ATTRIBUTE; default -> TabuMode.PLACEMENT_ATTRIBUTE;
                };
                return new TabuSearchSolver(rot, order, mode, record("tabu", quick ? new TabuSearchConfig(60,20,7,25,seed) : TabuSearchConfig.defaults(seed), seed), limit, decoder);
            }
            case SA -> {
                requireParameterGroups(Set.of("sa"));
                return new SimulatedAnnealingSolver(rot, order, record("sa", quick ? new SimulatedAnnealingConfig(50,30,.96,1e-5,15,.8,50,seed) : SimulatedAnnealingConfig.defaults(seed), seed), limit, decoder);
            }
            case ACO -> {
                requireParameterGroups(Set.of("aco"));
                return new AntColonySolver(rot, order, record("aco", quick ? new AntColonyConfig(20,40,1,2,.2,1,3,15,seed) : AntColonyConfig.defaults(seed), seed), limit, decoder);
            }
            case ILS -> {
                requireParameterGroups(Set.of("ils"));
                return new IteratedLocalSearchSolver(rot, order, record("ils", quick ? new IteratedLocalSearchConfig(40,30,2,15,seed) : IteratedLocalSearchConfig.defaults(seed), seed), limit, decoder);
            }
            default -> {
                Set<String> groups = id == AlgorithmId.ADAPTIVE_GA ? Set.of("ga","adaptive") : id == AlgorithmId.GA ? Set.of("ga") : Set.of("ga","hybrid");
                requireParameterGroups(groups);
                GeneticAlgorithmConfig ga = record("ga", quick ? new GeneticAlgorithmConfig(30,40,3,.9,.2,2,15,10,GeneticMutationMode.MIXED,seed) : new GeneticAlgorithmConfig(100,500,3,.9,.2,5,100,20,GeneticMutationMode.MIXED,seed), seed);
                if (id == AlgorithmId.ADAPTIVE_GA) return new AdaptiveGeneticAlgorithmSolver(rot, order, ga, record("adaptive", AdaptiveGeneticAlgorithmConfig.defaults(), seed), limit, decoder);
                HybridLocalSearchMode mode = id == AlgorithmId.GA_SA ? HybridLocalSearchMode.ANNEALING : id == AlgorithmId.GA_TABU ? HybridLocalSearchMode.TABU_MOVE : null;
                HybridGeneticAlgorithmConfig hybrid = mode == null ? null : record("hybrid", quick ? new HybridGeneticAlgorithmConfig(5,.1,3,60,3,10,.9,.3,5) : HybridGeneticAlgorithmConfig.defaults(), seed);
                return new GeneticAlgorithmSolver(rot, order, ga, mode, hybrid, limit, decoder);
            }
        }
    }

    private void requireParameterGroups(Set<String> groups) {
        value.path("parameters").fieldNames().forEachRemaining(k -> { if (!groups.contains(k)) throw new IllegalArgumentException("inactive parameter group: " + k); });
    }
    @SuppressWarnings("unchecked")
    private <T> T record(String group, T defaults, long seed) {
        try {
            Class<?> type = defaults.getClass(); RecordComponent[] fields = type.getRecordComponents();
            ObjectNode merged = JSON.valueToTree(defaults);
            JsonNode supplied = value.path("parameters").path(group);
            if (!supplied.isMissingNode() && !supplied.isObject()) throw new IllegalArgumentException("parameter group must be object");
            Set<String> names = new HashSet<>(); for (var f : fields) names.add(f.getName());
            supplied.fieldNames().forEachRemaining(k -> {
                if (!names.contains(k) || k.equals("seed")) throw new IllegalArgumentException("unknown parameter or embedded seed: " + group + "." + k);
                JsonNode node = supplied.get(k);
                if (node.isNull()) throw new IllegalArgumentException("null parameter: " + k);
                merged.set(k, node);
            });
            if (names.contains("seed")) merged.put("seed",seed);
            Object[] args = new Object[fields.length]; Class<?>[] types = new Class<?>[fields.length];
            for (int i=0;i<fields.length;i++) {
                var f=fields[i]; types[i]=f.getType(); JsonNode n=merged.get(f.getName());
                if (f.getType()==int.class && (!n.isIntegralNumber() || !n.canConvertToInt())) throw new IllegalArgumentException("expected int: " + f.getName());
                if (f.getType()==double.class && !n.isNumber()) throw new IllegalArgumentException("expected number: " + f.getName());
                args[i]=JSON.treeToValue(n,f.getType());
            }
            return (T)type.getDeclaredConstructor(types).newInstance(args);
        } catch (InvocationTargetException e) { throw new IllegalArgumentException("invalid " + group + ": " + e.getCause().getMessage(), e.getCause()); }
        catch (ReflectiveOperationException | java.io.IOException e) { throw new IllegalArgumentException("cannot construct " + group,e); }
    }

    public PackingDecoder decoder() {
        boolean rot=rotationAllowed(); String family=value.path("decoder").asText(); String variant=value.path("decoderVariant").asText();
        if (family.equals("NATIVE")) return PackingDecoders.create(PackingDecoderType.parse(variant),rot);
        String[] bits=variant.split(":",-1);
        Supplier<PackingAlgorithm> factory;
        switch (family) {
            case "SHELF" -> {
                if (bits.length!=2) throw new IllegalArgumentException("SHELF requires heuristic:WM");
                ShelfHeuristic h=ShelfHeuristic.valueOf(bits[0]); boolean wm=bool(bits[1]);
                factory=()->new ShelfSolver(rot,ItemOrder.INPUT,h,wm,ShelfSelectionMode.SEQUENTIAL);
            }
            case "SKYLINE" -> {
                if(bits.length!=2) throw new IllegalArgumentException("SKYLINE requires heuristic:WM");
                SkylinePlacementHeuristic h=SkylinePlacementHeuristic.valueOf(bits[0]); boolean wm=bool(bits[1]);
                factory=()->new SkylineGlobalSolver(rot,ItemOrder.INPUT,h,wm,SkylineSelectionMode.SEQUENTIAL);
            }
            case "MAXRECTS" -> {
                if(bits.length!=1) throw new IllegalArgumentException("MAXRECTS requires heuristic");
                MaxRectsHeuristic h=MaxRectsHeuristic.valueOf(bits[0]);
                factory=()->new MaxRectsSolver(rot,ItemOrder.INPUT,h,false);
            }
            case "GUILLOTINE" -> {
                if(bits.length!=3) throw new IllegalArgumentException("GUILLOTINE requires choice:split:merge");
                GuillotineRectChoice choice=GuillotineRectChoice.valueOf(bits[0]); GuillotineSplitRule split=GuillotineSplitRule.valueOf(bits[1]); boolean merge=bool(bits[2]);
                factory=()->new GuillotineSolver(rot,ItemOrder.INPUT,choice,split,merge,false);
            }
            default -> throw new IllegalArgumentException("unknown decoder family: "+family);
        }
        return new PackingDecoder() {
            public String name(){return "Existing-kernel adapter("+family+":"+variant+")";}
            public Solution solveInPriorityOrder(Instance instance,List<Item> items){return factory.get().solve(new Instance(instance.name(),instance.binWidth(),instance.binHeight(),items));}
            public long packedAreaInPriorityOrder(Instance instance,List<Item> items){return factory.get().packedAreaOf(new Instance(instance.name(),instance.binWidth(),instance.binHeight(),items));}
        };
    }
    private static boolean bool(String s){if(!s.equals("true")&&!s.equals("false"))throw new IllegalArgumentException("invalid boolean: "+s);return Boolean.parseBoolean(s);}
}
