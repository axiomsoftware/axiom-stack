package axiom.db.utils;

public class LuceneCompressor extends LuceneManipulator {
	
	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("Must specify the database dir to compress!");
			return;
		}
		
		new LuceneCompressor().compress(args[0]);
	}
	
}