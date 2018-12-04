import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);

		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}

	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {

		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			String code = codings[val];
			out.writeBits(code.length(), Integer.parseInt(code,2));
		}
		
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code,2));
	}

	private String[] makeCodingsFromTree(HuffNode root) {

		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"",encodings);
		return encodings;
	}

	private void codingHelper(HuffNode root, String path, String[] encodings) {

		if (root.myLeft==null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		}

		codingHelper(root.myLeft, path+"0", encodings);
		codingHelper(root.myRight, path+"1", encodings);
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {

		if (root.myLeft==null && root.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
		}
		else {
			out.writeBits(1, root.myValue);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
	}

	private int[] readForCounts(BitInputStream in) {
		int[] toReturn = new int[ALPH_SIZE+1];

		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			toReturn[val]++;
		}

		toReturn[PSEUDO_EOF]=1;
		return toReturn;
	}

	private HuffNode makeTreeFromCounts(int[] counts) {

		//NOTE: make sure pseudo is represented in the tree
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int i=0; i<counts.length; i++) {
			if (counts[i]>0)
				pq.add(new HuffNode(i,counts[i],null,null));
		}

		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight+right.myWeight, left, right);
			// create new HuffNode t with weight from
			// left.weight+right.weight and left, right subtrees
			pq.add(t);
		}
		HuffNode root = pq.remove();

		return root;
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		System.out.println("BITS :"+bits);
		if (bits != HUFF_TREE)
			throw new HuffException("illegal header starts with "+bits);

		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);

		out.close();
	}

	private HuffNode readTreeHeader(BitInputStream in) {
		//NOTE: might not read in just one bit

		int bit = in.readBits(1);
		System.out.println("bit: "+bit);

		if (bit == -1)
			throw new HuffException("Reading bits failed");
		//NOTE: might not be right with just in
		if (bit == 0) {
			System.out.println("before left "+in);
			HuffNode left = readTreeHeader(in);
			System.out.println("after left "+in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode (0,0,left, right);
		}
		else {

			int value = in.readBits(BITS_PER_WORD+1);
			System.out.println("Value: "+value);
			return new HuffNode(value, 0, null, null);
		}

	}

	private void readCompressedBits(HuffNode root, BitInputStream input, BitOutputStream output) {

		HuffNode current = root;   // root of tree, constructed from header data

		while (true) {
			int bits = input.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else { 

				// use the zero/one value of the bit read
				// to traverse Huffman coding tree
				// if a leaf is reached, decode the character and print UNLESS
				// the character is pseudo-EOF, then decompression done

				if (bits == 0) 
					current = current.myLeft; 
				else  
					current = current.myRight;

				if (current.myLeft == null && current.myRight == null) { // at leaf!
					if (current.myValue==PSEUDO_EOF) 
						break;
					else {
						output.writeBits(BITS_PER_WORD+1, current.myValue);
						current = root; // start back after leaf
					}
				}
			}
		}
	}
}