package fr.ybonnel.chitchat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ybonnel on 16/07/14.
 */
public class GenerateurData {

    public static void main(String[] args) throws IOException {
        File out = new File("/home/ybonnel/programmes/gatling-charts-highcharts-1.5.5/user-files/data/tweets.csv");
        FileWriter writer = new FileWriter(out);


        writer.append("author,text,thread,latest,search").append('\n');

        IntStream.iterate(1, i -> i+1)
                .filter(i -> i%2 != 0)
                .mapToObj(i -> i)
                .flatMap((i) ->
                                Stream.of(
                                        String.format(
                                                "%s,\"%s\",%d,%s,%s",
                                                "Loki" + i,
                                                "I have " + i + " army !",
                                                i,
                                                "Loki" + i,
                                                "hulk" + i + "x"
                                        ),
                                        String.format(
                                                "%s,\"%s\",%d,%s,%s",
                                                "Iron Man" + i,
                                                "I have " + i + " Hulk" + i + "x !",
                                                i,
                                                "Iron%20Man" + i,
                                                "hulk" + i + "x"
                                        ),
                                        String.format(
                                                "%s,\"%s\",%d,%s,%s",
                                                "Black Widow" + i,
                                                "Loki means to unleash the HULK" + i
                                                        + "x. Keep Banner in the lab, I'm on my way.",
                                                i+1,
                                                "Black%20Widow" + i,
                                                "hulk" + i + "x"
                                        )
                                )
                ).limit(100000)
                .forEach(line -> {
            try {
                writer.append((String) line).append('\n');
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        writer.close();

    }


}
