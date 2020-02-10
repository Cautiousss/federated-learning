import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FederatedModel {

    public static int numInputs = 45;
    public static int numOutputs = 10;
    public static int batchSize = 50;

    public static MultiLayerNetwork model = null;

    private static final String onDeviceModelPath = "res/onDeviceModel";
    private static final String serverModel = "res/model/server_model.zip";
    private static final String updatedModel = "res/model/server_model.zip";


    // average weights over mobile devices' models
    public void AverageWeights(int layer, double alpha) throws IOException {

        //original model
        Map<String, INDArray> paramTable = model.paramTable();
        INDArray weight = paramTable.get(String.format("%d_W", layer));
        INDArray bias = paramTable.get(String.format("%d_b", layer));
        INDArray avgWeights = weight.mul(alpha);
        INDArray avgBias = bias.mul(alpha);

        // average weights over mobile devices' models
        System.out.println("\nAveraging weights...");
        File dir = new File(onDeviceModelPath);
        File[] listOfFiles = dir.listFiles();
        System.out.println("listOfFiles.length:" + listOfFiles.length);
        List<File> files = new ArrayList<>();

        for (int i = 0; i < listOfFiles.length; i++) {
            // sometimes there are some strange files in current dir
            // so here we use contains("zip") to exclude those files
            if (listOfFiles[i].isFile() && (listOfFiles[i] + "").contains("zip")) {
                System.out.println(listOfFiles[i]);
                files.add(listOfFiles[i]);
            }
        }

        int len = files.size();
        MultiLayerNetwork transferred_model = null;
        for (int i = 0; i < len; i++) {
            try {
                transferred_model = ModelSerializer.restoreMultiLayerNetwork(files.get(i), false);
            } catch (IOException e) {
                e.printStackTrace();
            }
            paramTable = transferred_model.paramTable();
            weight = paramTable.get(String.format("%d_W", layer));
            bias = paramTable.get(String.format("%d_b", layer));

            avgWeights = avgWeights.add(weight.mul(1.0 - alpha).div(len));
            avgBias = avgBias.add(bias.mul(1.0 - alpha).div(len));
        }

        model.setParam(String.format("%d_W", layer), avgWeights);
        model.setParam(String.format("%d_b", layer), avgBias);

        System.out.println("\nWriting server model...");
        ModelSerializer.writeModel(model, serverModel, false);

        evaluateModel();

        delete(files);

    }

    public static void delete(List<File> files) {
        System.out.println("Deleting files...");
        int len = files.size();
        for (int i = 0; i < len; i++) {
            files.get(i).delete();
        }
        System.out.println("Files deleted");
    }

    public static void evaluateModel() {

        final String filenameTest = "res/dataset/test.csv";
        //Load the test/evaluation data:
        RecordReader rrTest = new CSVRecordReader();
        try {
            rrTest.initialize(new FileSplit(new File(filenameTest)));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        DataSetIterator testIter = new RecordReaderDataSetIterator(rrTest, batchSize, 0, 10);
        System.out.println("\nEvaluate model....");
        Evaluation eval = new Evaluation(numOutputs);
        while (testIter.hasNext()) {
            DataSet t = testIter.next();
            INDArray features = t.getFeatures();
            INDArray labels = t.getLabels();
            INDArray predicted = model.output(features, false);
            eval.eval(labels, predicted);
        }
        // Print the evaluation statistics
        System.out.println(eval.stats());

    }


    public void initModel() throws IOException {

        System.out.println("initing model...");

        int seed = 100;
        double learningRate = 0.001;
        int round = 10;
        int numHiddenNodes = 1000;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .weightInit(WeightInit.XAVIER)
                .updater(new Nesterovs(learningRate, 0.9))
                .list()
                .layer(new DenseLayer.Builder().nIn(numInputs).nOut(numHiddenNodes)
                        .activation(Activation.RELU)
                        .build())
                .layer(new DenseLayer.Builder().nIn(numHiddenNodes).nOut(numHiddenNodes)
                        .activation(Activation.RELU)
                        .build())
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .activation(Activation.SOFTMAX)
                        .nIn(numHiddenNodes).nOut(numOutputs).build())
                .build();

        model = new MultiLayerNetwork(conf);

        model.init();
        System.out.println("init model finish!\n");

        ModelSerializer.writeModel(model, serverModel, true);
        System.out.println("Write model to " + serverModel + " finish\n");

    }

    public static void main(String[] args) throws Exception {

//        initModel();
//
//        writeModel();

//        for (int t = 0; t < round; t++) {
//            System.out.println("\n Global Training Round:" + t + "\n");
//            System.out.println("selecting clients...");
//            selectClients(199);
//            System.out.println("AverageWeights...");
//            AverageWeights(2, 0.5);
//            evaluateModel();
//        }
        System.out.println("Done!");
    }

}