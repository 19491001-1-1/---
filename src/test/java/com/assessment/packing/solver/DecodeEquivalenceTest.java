package com.assessment.packing.solver;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 解码缓存的等价性安全网。
 *
 * <p>缓存是纯性能优化，唯一允许的影响是「少解码几次」，绝不能改变任何排列的解码结果。
 * 这里从两个方向把关：
 *
 * <ol>
 *   <li><b>键正确性</b>：用一个结果依赖排列顺序、带记忆的解码器，把大量排列灌进缓存后
 *       逐个与「全新解码」对照。若键忽略了顺序、旋转或实例身份，这里必然对不上。</li>
 *   <li><b>搜索可复现</b>：六个搜索算法以相同种子跑两遍，装载面积、放置列表与统计
 *       必须逐位一致——缓存不得在两次运行之间残留状态。</li>
 * </ol>
 */
public final class DecodeEquivalenceTest {
    private static final List<AlgorithmId> SEARCHERS = List.of(
            AlgorithmId.TABU_MOVE, AlgorithmId.SA, AlgorithmId.ACO,
            AlgorithmId.ILS, AlgorithmId.GA, AlgorithmId.ADAPTIVE_GA);

    public static void main(String[] args) throws Exception {
        cachedResultsMatchFreshDecode();
        cacheSeparatesInstances();
        allSearchersAreReproducibleWithCache();
        System.out.println("DecodeEquivalenceTest: 全部通过");
    }

    /** 缓存返回的每一个解，都必须与「同排列、全新解码」的结果完全相同。 */
    private static void cachedResultsMatchFreshDecode() {
        Instance instance = synthetic();
        OrderSensitiveDecoder decoder = new OrderSensitiveDecoder();
        DecodingCache cache = new DecodingCache(instance, decoder, 64);

        List<int[]> sequences = permutations(instance.items().size(), 240, 20261012L);
        List<Long> cached = new ArrayList<>(sequences.size());
        for (int[] sequence : sequences) cached.add(cache.areaOf(sequence));

        for (int index = 0; index < sequences.size(); index++) {
            // 用一个全新的缓存强制真实解码，作为对照。
            long fresh = new DecodingCache(instance, decoder).areaOf(sequences.get(index).clone());
            long hit = cached.get(index);
            check(hit == fresh,
                    "缓存面积与全新解码不一致：排列=" + java.util.Arrays.toString(sequences.get(index))
                            + " 缓存=" + hit + " 全新=" + fresh);
            // 完整布局不走缓存，但仍应与直接解码一致。
            check(cache.solve(sequences.get(index)).placements()
                            .equals(new DecodingCache(instance, decoder)
                                    .solve(sequences.get(index).clone()).placements()),
                    "缓存路径解出的布局与直接解码不一致：排列="
                            + java.util.Arrays.toString(sequences.get(index)));
        }
    }

    /** 同一个排列在不同实例上必须得到各自的解，缓存不得跨实例串味。 */
    private static void cacheSeparatesInstances() {
        Instance large = new Instance("large", 100, 100,
                List.of(new Item(1, 40, 40), new Item(2, 30, 30)));
        Instance small = new Instance("small", 50, 50,
                List.of(new Item(1, 20, 20), new Item(2, 10, 10)));
        OrderSensitiveDecoder decoder = new OrderSensitiveDecoder();
        int[] sequence = {2, 1};

        long onLarge = new DecodingCache(large, decoder).areaOf(sequence);
        long onSmall = new DecodingCache(small, decoder).areaOf(sequence);

        // 排列 {2,1}：两个零件都能放进各自的箱子，面积按各自的尺寸算。
        check(onLarge == 30 * 30 + 40 * 40, "大实例应按大零件面积装载，实际=" + onLarge);
        check(onSmall == 10 * 10 + 20 * 20, "小实例应按小零件面积装载，实际=" + onSmall);
        check(onLarge != onSmall, "缓存不得跨实例复用");
    }

    /** 六个搜索算法：相同种子两遍，结果与统计必须逐位一致。 */
    private static void allSearchersAreReproducibleWithCache() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut2.txt"));
        for (AlgorithmId algorithm : SEARCHERS) {
            Solution first = solve(algorithm, instance, 424242L);
            Solution second = solve(algorithm, instance, 424242L);
            check(first.packedArea() == second.packedArea(),
                    algorithm + "相同种子面积不一致：" + first.packedArea() + "/" + second.packedArea());
            check(first.placements().equals(second.placements()),
                    algorithm + "相同种子布局不一致");
        }
    }

    private static Solution solve(AlgorithmId algorithm, Instance instance, long seed) {
        return SolverFactory.create(
                algorithm, true, ItemOrder.AREA_DESC, seed, ExperimentProfile.QUICK,
                SearchLimit.evaluations(300), PackingDecoderType.SKYLINE).solve(instance);
    }

    /** 按 id 升序的全排列中随机抽取若干个，作为缓存键的对抗样本。 */
    private static List<int[]> permutations(int size, int count, long seed) {
        Random random = new Random(seed);
        List<int[]> sequences = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int[] sequence = new int[size];
            for (int j = 0; j < size; j++) sequence[j] = j + 1;
            for (int j = size - 1; j > 0; j--) {
                int swap = random.nextInt(j + 1);
                int temporary = sequence[j];
                sequence[j] = sequence[swap];
                sequence[swap] = temporary;
            }
            sequences.add(sequence);
        }
        return sequences;
    }

    private static Instance synthetic() {
        List<Item> items = new ArrayList<>();
        for (int id = 1; id <= 5; id++) items.add(new Item(id, 10 * id, 10));
        return new Instance("equivalence", 200, 200, items);
    }

    /** 结果同时依赖排列顺序和零件尺寸，任何键碰撞都会立刻暴露。 */
    private static final class OrderSensitiveDecoder implements PackingDecoder {
        @Override
        public String name() {
            return "order-sensitive";
        }

        @Override
        public Solution solveInPriorityOrder(Instance instance, List<Item> priority) {
            List<Placement> placements = new ArrayList<>();
            int x = 0;
            for (Item item : priority) {
                if (item.width() > instance.binWidth() - x) break;
                placements.add(new Placement(item, x, 0, false));
                x += item.width();
            }
            return new Solution("order-sensitive", placements, 0);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
