package yatan.deeplearning.wordembedding.actor.impl;

import java.io.Serializable;

import java.util.Date;
import java.util.List;
import java.util.Random;

import scala.actors.threadpool.Arrays;

import yatan.ann.AnnData;
import yatan.ann.AnnModel;
import yatan.ann.AnnTrainer;
import yatan.deeplearning.wordembedding.model.WordEmbedding;
import yatan.deeplearning.wordembedding.model.WordEmbeddingTrainingInstance;
import yatan.distributedcomputer.Data;
import yatan.distributedcomputer.Parameter;
import yatan.distributedcomputer.contract.impl.AbstractComputeActorContractImpl;

public class ComputeActorWordEmbeddingEvaluatorImpl extends AbstractComputeActorContractImpl {
    private static final int EVALUATION_INTERVAL_IN_SECONDS = 60;

    @Override
    protected int requestDataSize() {
        return 10000;
    }

    @Override
    protected ComputeResult doCompute(List<Data> dataset, Parameter parameter) {
        Serializable[] parameters = (Serializable[]) parameter.getSerializable();
        WordEmbedding wordEmbedding = (WordEmbedding) parameters[0];
        AnnModel annModel = (AnnModel) parameters[1];

        double totalPositiveScore = 0;
        int positiveCount = 0;
        double totalNegativeScore = 0;
        int negativeCount = 0;

        AnnTrainer trainer = new AnnTrainer();
        int accurate = 0;
        for (Data data : dataset) {
            WordEmbeddingTrainingInstance instance = (WordEmbeddingTrainingInstance) data.getSerializable();

            // first convert input data into word embedding
            // FIXME: could reuse an array, no need to allocate it every time
            double[] input = new double[instance.getInput().size() * wordEmbedding.getWordVectorSize()];
            for (int i = 0; i < instance.getInput().size(); i++) {
                int index = instance.getInput().get(i);
                for (int j = 0; j < wordEmbedding.getWordVectorSize(); j++) {
                    input[i * wordEmbedding.getWordVectorSize() + j] = wordEmbedding.getMatrix().getData()[j][index];
                }
            }

            AnnData annData = new AnnData(input, new double[] {instance.getOutput()});

            // train with this ann data instance and update gradient
            double[][] output = trainer.run(annModel, annData.getInput(), new double[annModel.getLayerCount()][]);
            // System.out.println(output[annModel.getLayerCount() - 1][0]);
            if (annData.getOutput()[0] > 0) {
                if (output[annModel.getLayerCount() - 1][0] > 0.5) {
                    accurate++;
                }

                totalPositiveScore += output[annModel.getLayerCount() - 1][0];
                positiveCount++;
            } else {
                if (output[annModel.getLayerCount() - 1][0] < 0.5) {
                    accurate++;
                }

                totalNegativeScore += output[annModel.getLayerCount() - 1][0];
                negativeCount++;
            }
        }

        getLogger().info("Average positive score: " + totalPositiveScore / positiveCount);
        getLogger().info("Average negative score: " + totalNegativeScore / negativeCount);
        getLogger().info("Precision: " + 100.0 * accurate / requestDataSize() + "%");
        logWordEmbedding(wordEmbedding, "的");
        logWordEmbedding(wordEmbedding, "吴");
        logWordEmbedding(wordEmbedding, "煮");
        printAnnModel(annModel);

        // evaluate some word distance rank
        // evaluateWordDistanceRank(wordEmbedding, "france", "germany", "greece", "spain", "america", "canada", "china",
        // "denmark", "egypt", "australia", "brazil");
        evaluateWordDistanceRank(wordEmbedding, "看", "见", "视", "瞧", "瞄", "目", "相", "窥", "探", "扫", "瞪", "望");
        evaluateWordDistanceRank(wordEmbedding, "吴", "赵", "钱", "孙", "李", "周", "郑", "王", "冯", "陈", "褚", "卫");

        // return computation result
        ComputeResult result = new ComputeResult();
        result.setRepeat(true);
        result.setRepeatDelayInSeconds(EVALUATION_INTERVAL_IN_SECONDS);
        result.setGradient(null);

        return result;
    }

    private void printAnnModel(AnnModel annModel) {
        Random random = new Random(new Date().getTime());
        getLogger().info(
                Arrays.toString(annModel.getLayer(0).getData()[random.nextInt(annModel.getLayer(0).getData().length)]));
        getLogger().info(Arrays.toString(annModel.getLayer(1).getData()[0]));
    }

    private void evaluateWordDistanceRank(WordEmbedding wordEmbedding, String word, String... candidates) {
        int[] rank = new int[candidates.length];
        int wordIndex = wordEmbedding.getDictionary().indexOf(word);
        double[] distances = new double[wordEmbedding.getDictionary().size()];
        for (int i = 0; i < distances.length; i++) {
            distances[i] = wordEmbedding.distanceBetween(wordIndex, i);
        }

        for (int i = 0; i < candidates.length; i++) {
            double distance =
                    wordEmbedding.distanceBetween(wordIndex, wordEmbedding.getDictionary().indexOf(candidates[i]));
            for (double otherDistance : distances) {
                if (otherDistance < distance) {
                    rank[i]++;
                }
            }
        }

        StringBuilder sb = new StringBuilder("Distance ranking for '" + word + "': ");
        for (int i = 0; i < candidates.length; i++) {
            sb.append(candidates[i] + " = " + rank[i] + ", ");
        }
        getLogger().info(sb.toString());
        System.out.println(sb.toString());
    }

    private void logWordEmbedding(WordEmbedding wordEmbedding, String word) {
        StringBuilder sb = new StringBuilder();
        int index = wordEmbedding.indexOf(word);
        for (int i = 0; i < wordEmbedding.getWordVectorSize(); i++) {
            sb.append(wordEmbedding.getMatrix().getData()[i][index]).append(" ");
        }
        getLogger().info(sb.toString());
    }
}
