package edu.uta.cse6331;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;

import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

class Vertex implements Writable {

	int tag; // 0 for a graph vertex, 1 for a group number
	long group; // the group where this vertex belongs to
	long v_id; // the vertex ID
	ArrayList<Long> adj;

	public Vertex() {
		
	}

	Vertex(int tag, long group, long v_id, ArrayList<Long> adj) {
		this.tag = tag;
		this.group = group;
		this.v_id = v_id;
		this.adj = adj;
	}

	Vertex(int tag, long group) {
		this.tag = tag;
		this.group = group;
		this.v_id = 0;
		this.adj = new ArrayList<>();
	}


	
	public int getTag() {
		return tag;
	}

	public void setTag(int tag) {
		this.tag = tag;
	}

	public long getGroup() {
		return group;
	}

	public void setGroup(long group) {
		this.group = group;
	}

	public long getV_id() {
		return v_id;
	}

	public void setV_id(long v_id) {
		this.v_id = v_id;
	}

	public ArrayList<Long> getAdj() {
		return adj;
	}

	public void setAdj(ArrayList<Long> adj) {
		this.adj = adj;
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		// TODO Auto-generated method stub
		adj = new ArrayList<Long>();
		tag = in.readInt();
		group = in.readLong();
		v_id = in.readLong();
		int list_size = in.readInt();
		for (int counter = 0; counter < list_size; counter++) {
			long size_item = in.readLong();
			adj.add(size_item);
		}
	}

	@Override
	public void write(DataOutput out) throws IOException {
		// TODO Auto-generated method stub

		out.writeInt(tag);
		out.writeLong(group);
		out.writeLong(v_id);
		out.writeInt(adj.size());
		for (long val : adj) {
			out.writeLong(val);
		}
	}

		public String toString() {
		return tag + " " + group  + " " + v_id + " " + adj.toString();
	}
}

public class Graph {

	public static class First_Mapper extends Mapper<Object, Text, LongWritable, Vertex> {
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String s = value.toString();
			String[] arr = s.split(",");
			ArrayList<Long> Adj_Array = new ArrayList<Long>();
			
			for (int i = 1; i < arr.length; i++) {
				Adj_Array.add(Long.parseLong(arr[i]));
			}

			Vertex v = new Vertex(0, Long.parseLong(arr[0]), Long.parseLong(arr[0]), Adj_Array);

			context.write(new LongWritable(Integer.parseInt(arr[0])), v);
		}
	}

	public static class Second_Mapper extends Mapper<LongWritable, Vertex, LongWritable, Vertex> {
		public void map(LongWritable key, Vertex val, Context context) throws IOException, InterruptedException {
			Vertex v = val;
			context.write(new LongWritable(v.getV_id()), v);

			for (long a : v.getAdj()) {
				context.write(new LongWritable(a), new Vertex(1, v.getGroup()));
			}
		}
	}

	public static class First_Reducer extends Reducer<LongWritable, Vertex, LongWritable, Vertex> {
		public void reduce(LongWritable key, Iterable<Vertex> val, Context context)
				throws IOException, InterruptedException {
			long m = Long.MAX_VALUE;

			ArrayList<Long> temp_adj = new ArrayList<Long>();

			for (Vertex v1 : val) {
				if (v1.getTag() == 0) {
					temp_adj = v1.getAdj();
				}
				m = Math.min(m, v1.getGroup());
			}
			context.write(new LongWritable(m), new Vertex(0, m, key.get(), temp_adj));
		}
	}

	public static class Final_Mapper extends Mapper<LongWritable, Vertex, LongWritable, LongWritable> {
		long a = 1;

		public void map(LongWritable key, Vertex val, Context context) throws IOException, InterruptedException {
			context.write(key, new LongWritable(a));
		}
	}

	public static class Final_Reducer extends Reducer<LongWritable, LongWritable, LongWritable, LongWritable> {
		public void reduce(LongWritable key, Iterable<LongWritable> val, Context context)
				throws IOException, InterruptedException {
			long m = 0;
			for (LongWritable value : val) {
				m = m + value.get();
			}
			context.write(key, new LongWritable(m));
		}
	}

	public static void main(String[] args) throws Exception {

		Job job = Job.getInstance();
		job.setJobName("First Map-Reduce Step");
		job.setJarByClass(Graph.class);

		job.setMapperClass(First_Mapper.class);

		job.setOutputKeyClass(LongWritable.class);
		job.setOutputValueClass(Vertex.class);

		job.setOutputFormatClass(SequenceFileOutputFormat.class);

		FileInputFormat.addInputPath(job, new Path(args[0]));
		SequenceFileOutputFormat.setOutputPath(job, new Path(args[1]+"/f0"));
		job.waitForCompletion(true);

		for (int i = 0; i < 5; i++) {
			Job job1 = Job.getInstance();
			job1.setJobName("Second Map-Reduce Step");
			job1.setJarByClass(Graph.class);

			job1.setMapperClass(Second_Mapper.class);
			job1.setReducerClass(First_Reducer.class);
			
			job1.setMapOutputKeyClass(LongWritable.class);
			job1.setMapOutputValueClass(Vertex.class);
			
			job1.setOutputKeyClass(LongWritable.class);
			job1.setOutputValueClass(Vertex.class);
			
			job1.setInputFormatClass(SequenceFileInputFormat.class);
			job1.setOutputFormatClass(SequenceFileOutputFormat.class);
			
			SequenceFileInputFormat.setInputPaths(job1, new Path(args[1]+"/f"+i));
			SequenceFileOutputFormat.setOutputPath(job1, new Path(args[1]+"/f"+(i+1)));
			job1.waitForCompletion(true);
		}

		Job job2 = Job.getInstance();
		job2.setJobName("Final Step");
		job2.setJarByClass(Graph.class);
		
		job2.setMapperClass(Final_Mapper.class);
		job2.setReducerClass(Final_Reducer.class);
		
		job2.setMapOutputKeyClass(LongWritable.class);
		job2.setMapOutputValueClass(LongWritable.class);
		
		job2.setOutputKeyClass(LongWritable.class);
		job2.setOutputValueClass(LongWritable.class);
		
		job2.setInputFormatClass(SequenceFileInputFormat.class);
		job2.setOutputFormatClass(TextOutputFormat.class);
		
		SequenceFileInputFormat.setInputPaths(job2, new Path(args[1]+"/f5"));
		FileOutputFormat.setOutputPath(job2, new Path(args[2]));
		
		job2.waitForCompletion(true);
	}
}
