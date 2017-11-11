package edu.uta.cse6331;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.text.html.parser.Element;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.ReflectionUtils;

class Elem implements Writable {
	int tag; // 0 for M, 1 for N
	int index; 
	double value;

	Elem() {
		tag = 0;
		index = 0;
		value = 0.0;
	}

	Elem(int tag, int index, double value) {
		this.tag = tag;
		this.index = index;
		this.value = value;
	}

	public void readFields(DataInput in) throws IOException {
		// TODO Auto-generated method stub
		tag = in.readInt();
		index = in.readInt();
		value = in.readDouble();
	}

	public void write(DataOutput out) throws IOException {
		// TODO Auto-generated method stub
		out.writeInt(tag);
		out.writeInt(index);
		out.writeDouble(value);
	}
}

class Pair implements WritableComparable<Pair> {
	int i;
	int j;

	Pair() {
		i = 0;
		j = 0;
	}

	Pair(int i, int j) {
		this.i = i;
		this.j = j;
	}

	public void readFields(DataInput in) throws IOException {
		// TODO Auto-generated method stub

		i = in.readInt();
		j = in.readInt();
	}

	public void write(DataOutput out) throws IOException {
		// TODO Auto-generated method stub
		out.writeInt(i);
		out.writeInt(j);
	}

	public int compareTo(Pair comp) {
		// TODO Auto-generated method stub
		if (i > comp.i) {
			return 1;
		} else if (i < comp.i) {
			return -1;
		} else {
			if (j > comp.j) {
				return 1;
			} else if (j < comp.j) {
				return -1;
			}
		}
		return 0;
	}

	public String toString() {
		return i + " " + j + " ";
	}
}

public class Multiply {

	public static class M_Mapper extends Mapper<Object, Text, IntWritable, Elem> {

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String l = value.toString();
			String[] matrixelem = l.split(",");

			Elem e = new Elem(0, Integer.parseInt(matrixelem[0]), Double.parseDouble(matrixelem[2]));

			context.write(new IntWritable(Integer.parseInt(matrixelem[1])), e);
		}
	}

	public static class N_Mapper extends Mapper<Object, Text, IntWritable, Elem> {

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String l = value.toString();
			String[] matrixelem = l.split(",");

			Elem e = new Elem(1, Integer.parseInt(matrixelem[1]), Double.parseDouble(matrixelem[2]));

			context.write(new IntWritable(Integer.parseInt(matrixelem[0])), e);
		}
	}

	public static class First_Reducer extends Reducer<IntWritable, Elem, Pair, DoubleWritable> {
		public void reduce(IntWritable key_in, Iterable<Elem> val, Context context)
				throws IOException, InterruptedException {
			ArrayList<Elem> Mat_M = new ArrayList<Elem>();
			ArrayList<Elem> Mat_N = new ArrayList<Elem>();

			Configuration conf = context.getConfiguration();

			for (Elem element : val) {
				Elem TempElem = ReflectionUtils.newInstance(Elem.class, conf);
				ReflectionUtils.copy(conf, element, TempElem);

				if (TempElem.tag == 0) {
					Mat_M.add(TempElem);
				} else if (TempElem.tag == 1) {
					Mat_N.add(TempElem);
				}

			}
			for (int i = 0; i < Mat_M.size(); i++) {
				for (int j = 0; j < Mat_N.size(); j++) {
					context.write(new Pair(Mat_M.get(i).index, Mat_N.get(j).index),new DoubleWritable(Mat_M.get(i).value * Mat_N.get(j).value));
				}
			}
		}
	}

	public static class Final_Mapper extends Mapper<Object, Text, Pair, DoubleWritable> {
		public void map(Object key, Text val, Context context) throws IOException, InterruptedException {

			String l = val.toString();
			String[] res = l.split(" ");

			Pair p = new Pair(Integer.parseInt(res[0]), Integer.parseInt(res[1]));
			DoubleWritable value = new DoubleWritable(Double.parseDouble(res[2]));

			context.write(p, value);
		}
	}

	public static class Final_Reducer extends Reducer<Pair, DoubleWritable, Pair, DoubleWritable> {
		public void reduce(Pair pair, Iterable<DoubleWritable> value, Context context)
				throws IOException, InterruptedException {

			double m = 0;
			for (DoubleWritable val : value) {
				m += val.get();
			}
			context.write(pair, new DoubleWritable(m));
		}
	}

	public static void main(String[] args) throws Exception {
		Job job1 = Job.getInstance();
		job1.setJobName("First Map-Reduce Step");
		job1.setJarByClass(Multiply.class);

		//First Map Job
		MultipleInputs.addInputPath(job1, new Path(args[0]), TextInputFormat.class, M_Mapper.class);
		MultipleInputs.addInputPath(job1, new Path(args[1]), TextInputFormat.class, N_Mapper.class);
		job1.setMapOutputKeyClass(IntWritable.class);
		job1.setMapOutputValueClass(Elem.class);

		job1.setReducerClass(First_Reducer.class);
		job1.setOutputKeyClass(Pair.class);
		job1.setOutputValueClass(DoubleWritable.class);
		job1.setOutputFormatClass(TextOutputFormat.class);
		FileOutputFormat.setOutputPath(job1, new Path(args[2]));
		job1.waitForCompletion(true);

		Job job = Job.getInstance();
		job.setJobName("Final Step");
		job.setJarByClass(Multiply.class);

		job.setMapperClass(Final_Mapper.class);
		job.setMapOutputKeyClass(Pair.class);
		job.setMapOutputValueClass(DoubleWritable.class);

		job.setReducerClass(Final_Reducer.class);
		job.setOutputKeyClass(Pair.class);
		job.setOutputValueClass(DoubleWritable.class);

		FileInputFormat.setInputPaths(job, new Path(args[2]));
		FileOutputFormat.setOutputPath(job, new Path(args[3]));

		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);
		job.waitForCompletion(true);
	}
}