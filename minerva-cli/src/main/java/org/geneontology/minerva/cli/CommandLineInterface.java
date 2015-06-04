package org.geneontology.minerva.cli;


public class CommandLineInterface {

	public static void main(String[] args) throws Exception {
		MinervaCommandRunner cr = new MinervaCommandRunner();
		cr.run(args);
	}
	
}
