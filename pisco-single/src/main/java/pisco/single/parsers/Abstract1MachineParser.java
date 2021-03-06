package pisco.single.parsers;

import parser.instances.AbstractTextParser;
import pisco.common.ITJob;

public abstract class Abstract1MachineParser extends AbstractTextParser {

	public int nbJobs;
	public int freezeTime;
	public int[] appearanceDates;
	public ITJob[] jobs;
	public int[] earlinessPenalties;
	public int[] tardinessPenalties;
	public int[][] setupTimes;

	public Abstract1MachineParser() {
		super();
	}

	public abstract boolean hasSetupTimes();
	
	public abstract boolean hasDeadlines();
	
	@Override
	public void cleanup() {
		super.cleanup();
		nbJobs = 0;
		jobs = null;
		freezeTime = 0;
		appearanceDates = earlinessPenalties = tardinessPenalties = null;
		setupTimes = null;
	}

	public String getParserMsg() {
		return this.getClass().getSimpleName() + " PARSER    ";
	}
}