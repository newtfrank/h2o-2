package hex;

import junit.framework.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.ParseDataset2;
import water.util.Log;

import java.util.Random;

public class KMeans2RandomTest extends TestUtil {
  @BeforeClass public static void stall() {
    stall_till_cloudsize(JUnitRunnerDebug.NODES);
  }

  @Test
  @Ignore //currently fails
  public void run() {
    long seed = 0xDECAF;
    Random rng = new Random(seed);
    String[] datasets = new String[2];
    int[][] responses = new int[datasets.length][];
    datasets[0] = "smalldata/./logreg/prostate.csv";
    responses[0] = new int[]{1, 2, 8}; //CAPSULE (binomial), AGE (regression), GLEASON (multi-class)
    datasets[1] = "smalldata/iris/iris.csv";
    responses[1] = new int[]{4}; //Iris-type (multi-class)


    int testcount = 0;
    int count = 0;
    for (int i = 0; i < datasets.length; ++i) {
      String dataset = datasets[i];
      Key file = NFSFileVec.make(find_test_file(dataset));
      Frame frame = ParseDataset2.parse(Key.make(), new Key[]{file});
      Key vfile = NFSFileVec.make(find_test_file(dataset));
      Frame vframe = ParseDataset2.parse(Key.make(), new Key[]{vfile});

      for (int clusters : new int[]{1,10}) {
        for (int max_iter : new int[]{1,10,100}) {
          for (boolean normalize : new boolean[]{false, true}) {
            for (boolean drop_na_cols : new boolean[]{false, true}) {
              for (KMeans2.Initialization init : new KMeans2.Initialization[]{
                      KMeans2.Initialization.Furthest,
                      KMeans2.Initialization.None,
                      KMeans2.Initialization.PlusPlus}) {
                count++;

                KMeans2 k = new KMeans2();
                k.k = clusters;
                k.initialization = init;
                k.destination_key = Key.make();
                k.seed = 0xC0FFEE;
                k.source = frame;
                k.max_iter = max_iter;
                k.normalize = normalize;
                k.drop_na_cols = drop_na_cols;
                k.invoke();

                KMeans2.KMeans2Model m = UKV.get(k.dest());
                for (double d : m.between_cluster_variances) Assert.assertFalse(Double.isNaN(d));
                for (double d : m.within_cluster_variances) Assert.assertFalse(Double.isNaN(d));
                Assert.assertFalse(Double.isNaN(m.between_cluster_SS));
                Assert.assertFalse(Double.isNaN(m.total_SS));
                Assert.assertFalse(Double.isNaN(m.total_within_SS));
                for (long o : m.size) Assert.assertTrue(o > 0); //have at least one point per centroid
                for (double[] dc : m.centers) for (double d : dc) Assert.assertFalse(Double.isNaN(d));

                // make prediction (cluster assignment)
                Frame score = m.score(frame);
                for (long j=0; j<score.numRows(); ++j) org.junit.Assert.assertTrue(score.anyVec().at8(j) >= 0 && score.anyVec().at8(j) < clusters);
                score.delete();

                Log.info("Parameters combination " + count + ": PASS");
                testcount++;

                m.delete();
              }
            }
          }
        }
      }

      frame.delete();
      vframe.delete();
    }
    Log.info("\n\n=============================================");
    Log.info("Tested " + testcount + " out of " + count + " parameter combinations.");
    Log.info("=============================================");
  }
}