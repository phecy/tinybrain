package yatan.deeplearning.sequence.softmax.contract;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.Random;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import yatan.ann.AnnConfiguration;
import yatan.ann.AnnGradient;
import yatan.ann.DefaultAnnModel;
import yatan.deeplearning.wordembedding.model.Dictionary;
import yatan.deeplearning.wordembedding.model.WordEmbedding;
import yatan.distributed.akka.BaseActorContract;
import yatan.distributedcomputer.Parameter;
import yatan.distributedcomputer.Parameter.ParameterIndexPath;
import yatan.distributedcomputer.actors.ParameterActor;
import yatan.distributedcomputer.contract.ParameterActorContract;

public class CRFANNParameterActorContractImpl extends BaseActorContract implements ParameterActorContract {
    @Inject
    private Dictionary dictionary;
    @Inject
    @Named("ann_configuration")
    private AnnConfiguration annConfiguration;
    @Inject
    @Named("word_vector_size")
    private int wordVectorSize;
    @Inject
    @Named("tag_count")
    private int tagCount;

    private WordEmbedding wordEmbedding;
    private DefaultAnnModel annModel;
    private double[][] tagTransitionWeights;

    @Override
    public void requestParameters(ParameterIndexPath start, ParameterIndexPath end) {
        initIfNecessary();

        Parameter parameter = new Parameter();
        parameter.setSerializable(new Serializable[] {wordEmbedding, annModel, this.tagTransitionWeights});

        tellSender(new ParameterActor.ReceiveParameterMessage(getActor().getMessage(), parameter));
    }

    @Override
    public void updateGradient(Parameter gradient) {
        Preconditions.checkArgument(gradient != null, "Gradient cannot be null.");

        Serializable[] inputData = (Serializable[]) gradient.getSerializable();
        AnnGradient annGradient = (AnnGradient) inputData[0];

        if (annGradient != null) {
            // annModel.update(annGradient, ADA_DELTA_RHO, ADA_DELTA_EPSILON, annDeltaGradientSumSquare,
            // this.deltaAnnWeightSumSquare);
            // annModel.update(annGradient, 0.1, this.annDeltaGradientSumSquare);
            annModel.update(annGradient, 0.01);

            @SuppressWarnings("unchecked")
            Map<Integer, Double[]> wordEmbeddingDelta = (Map<Integer, Double[]>) inputData[1];
            wordEmbedding.update(wordEmbeddingDelta, 0.01);
            // wordEmbedding.update(wordEmbeddingDelta, ADA_DELTA_RHO, ADA_DELTA_EPSILON,
            // this.wordEmbeddingGradientSumSquare,
            // this.deltaWordEmbeddingSumSquare);
            // wordEmbedding.update(wordEmbeddingDelta, 0.1, this.wordEmbeddingGradientSumSquare);

            double[][] transitaionGradient = (double[][]) inputData[2];
            for (int i = 0; i < transitaionGradient.length; i++) {
                for (int j = 0; j < transitaionGradient[i].length; j++) {
                    this.tagTransitionWeights[i][j] +=
                            0.01 * (transitaionGradient[i][j] - 1 * this.tagTransitionWeights[i][j]);

                    // this.tagTransitionWeights[i][j] += 0.01 * transitaionGradient[i][j];
                }
            }
        }
    }

    private void initIfNecessary() {
        if (wordEmbedding == null) {
            getLogger().info("Initializing parameter actor...");

            if (wordEmbedding == null) {
                getLogger().info(
                        "Create new random word embedding with dictionary size = " + this.dictionary.words()
                                + ", word vector size = " + wordVectorSize);
                wordEmbedding = new WordEmbedding(this.dictionary.words(), wordVectorSize);
            }

            if (annModel == null) {
                getLogger().info("Create new ANN model with configuration " + this.annConfiguration);
                // Configuration configuration =
                // new Configuration(wordEmbedding.getWordVectorSize() * TrainingInstanceProducer.WINDOWS_SIZE);
                // configuration.addLayer(300, ActivationFunction.TANH);
                // configuration.addLayer(DataCenter.tags().size(), ActivationFunction.SOFTMAX, false);
                annModel = new DefaultAnnModel(this.annConfiguration);
            }

            if (this.tagTransitionWeights == null) {
                Random random = new Random(new Date().getTime());
                this.tagTransitionWeights = new double[tagCount + 2][tagCount + 2];
                for (int i = 0; i < this.tagTransitionWeights.length; i++) {
                    for (int j = 0; j < this.tagTransitionWeights[i].length; j++) {
                        this.tagTransitionWeights[i][j] = (random.nextDouble() - 0.5) / 5;
                    }
                }
            }
        }
    }
}