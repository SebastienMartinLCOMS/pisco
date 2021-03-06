package pisco.single;

import static pisco.common.JobUtils.*;
import static choco.Choco.MAX_UPPER_BOUND;
import static choco.Choco.constant;
import static choco.Choco.eq;
import static choco.Choco.geq;
import static choco.Choco.makeIntVar;
import static choco.Choco.makeIntVarArray;
import static choco.Choco.max;
import static choco.Choco.minus;
import static choco.Choco.precedence;
import static choco.Choco.precedenceDisjoint;
import static choco.kernel.common.util.tools.VariableUtils.*;
import static choco.cp.solver.search.BranchingFactory.*;
import static pisco.common.JobComparators.getCompositeComparator;
import static pisco.common.JobComparators.getShortestProcessingTime;
import static pisco.common.JobUtils.dueDates;
import static pisco.common.JobUtils.maxDueDate;
import static pisco.common.JobUtils.maxReleaseDate;
import static pisco.common.JobUtils.minDueDate;
import static pisco.common.JobUtils.sumDurations;

import java.util.Arrays;

import parser.instances.BasicSettings;
import pisco.common.CostFactory;
import pisco.common.DisjunctiveSettings;
import pisco.common.ICostFunction;
import pisco.common.ITJob;
import pisco.common.JobComparators;
import pisco.common.JobUtils;
import pisco.common.PDR1Scheduler;
import pisco.common.Pmtn1Scheduler;
import pisco.common.SchedulingBranchingFactory;
import pisco.common.choco.branching.LexMaxFakeBranching;
import pisco.single.choco.constraints.ModifyDueDateManager;
import pisco.single.choco.constraints.RelaxLmaxConstraint;
import pisco.single.choco.constraints.RelaxLmaxManager;
import pisco.single.parsers.Abstract1MachineParser;
import choco.Options;
import choco.cp.solver.CPSolver;
import choco.cp.solver.preprocessor.PreProcessCPSolver;
import choco.cp.solver.search.integer.branching.AssignOrForbidIntVarValPair;
import choco.cp.solver.search.task.profile.ProfileSelector;
import choco.kernel.common.util.tools.ArrayUtils;
import choco.kernel.model.Model;
import choco.kernel.model.ModelException;
import choco.kernel.model.constraints.ComponentConstraint;
import choco.kernel.model.variables.integer.IntegerVariable;
import choco.kernel.solver.Configuration;
import choco.kernel.solver.ContradictionException;
import choco.kernel.solver.Solver;
import choco.kernel.solver.branch.AbstractIntBranchingStrategy;
import choco.kernel.solver.search.integer.IntVarValPair;
import choco.kernel.solver.search.integer.VarValPairSelector;
import choco.kernel.solver.variables.integer.IntDomainVar;
import choco.kernel.visu.VisuFactory;
import choco.visu.components.chart.ChocoChartFactory;

public class SingleMachineLmax extends Abstract1MachineProblem {

	private IntegerVariable objVar;

	private IntegerVariable[] dueDates;

	private ComponentConstraint relaxConstraint;

	public SingleMachineLmax(BasicSettings settings,
			Abstract1MachineParser parser) {
		super(settings, parser, CostFactory.makeMaxCosts());
	}


	@Override
	public ICostFunction getCostFunction() {
		return CostFactory.getLateness();
	}

	@Override
	public Boolean preprocess() {
		if(defaultConf.readBoolean(SingleMachineSettings.INITIAL_LOWER_BOUND)) {
			final ITJob[] lbjobs = Arrays.copyOf(jobs, jobs.length);
			setComputedLowerBound(PDR1Scheduler.schedule1Lmax(lbjobs));
			SingleMachineRHeuristic heuristic = (SingleMachineRHeuristic) getHeuristic();
			if(JobUtils.isScheduledInTimeWindows(lbjobs)) {
				heuristic.storeSolution(lbjobs, getComputedLowerBound());
			} else {
				JobUtils.resetSchedule(lbjobs);
				final int lb = Pmtn1Scheduler.schedule1PrecLmax(lbjobs);

				if(! JobUtils.isInterrupted(lbjobs)) {
					setComputedLowerBound(lb);
					heuristic.storeSolution(lbjobs, getComputedLowerBound());	
				} else if(lb > getComputedLowerBound()) {
					setComputedLowerBound(lb);
				}
			}
		} else {
			setComputedLowerBound( JobUtils.minSlackTime(jobs));
		}
		return super.preprocess();
	}



	@Override
	public void initialize() {
		super.initialize();
		objVar = null;
		dueDates = null;
	}


	@Override
	protected int getHorizon() {
		return isFeasible() == Boolean.TRUE ? JobUtils.maxDueDate(jobs)  + objective.intValue(): maxReleaseDate(jobs) + sumDurations(jobs);
	}


	@Override
	public Model buildModel() {
		final Model model = super.buildModel();
		objVar = buildObjective("Lmax",MAX_UPPER_BOUND);
		if(defaultConf.readBoolean(SingleMachineSettings.MODIFY_DUE_DATES)) {
			///////////
			//Create Due date Variables 
			dueDates = new IntegerVariable[nbJobs];
			//The job with smallest due date is scheduled last
			final int minDueDate = minDueDate(jobs) - sumDurations(jobs);
			for (int i = 0; i < tasks.length; i++) {
				dueDates[i] = makeIntVar("D"+i, minDueDate, jobs[i].getDueDate(), 
						Options.V_BOUND, Options.V_NO_DECISION);
			}
			///////////
			//Add constraints which modify Due Dates on the fly
			int idx=0;
			for (int i = 0; i < tasks.length; i++) {
				for (int j = i+1; j < tasks.length; j++) {
					model.addConstraint( new ComponentConstraint( ModifyDueDateManager.class, null, 
							new IntegerVariable[]{dueDates[i], constant(jobs[j].getDuration()), 
						dueDates[j], constant(jobs[i].getDuration()), disjuncts[idx]})
							);
					idx++;
				}
			}
		} else dueDates = constDueDates(jobs);
		///////////
		//state lateness constraints
		IntegerVariable[] lateness = makeIntVarArray("L", nbJobs, 
				- maxDueDate(jobs), makespan.getUppB() - minDueDate(jobs), 
				Options.V_BOUND, Options.V_NO_DECISION);
		for (int i = 0; i < nbJobs; i++) {
			model.addConstraint(eq(lateness[i], minus(tasks[i].end(), dueDates[i])));
		}	


		///////////
		//create objective constraints
		model.addConstraints(
				max(lateness, objVar),
				geq( objVar, minus(makespan,maxDueDate(jobs)))
				);



		if( defaultConf.readBoolean(SingleMachineSettings.TASK_ORDERING) && ! hasSetupTimes() ) {
			////////////
			//Add pre-ordering constraints from dominance conditions
			final ITJob[] sjobs = Arrays.copyOf(jobs, nbJobs);
			Arrays.sort(sjobs, getCompositeComparator(getShortestProcessingTime(), JobComparators.getEarliestReleaseDate()));
			for (int i = 0; i < nbJobs - 1; i++) {
				final int d = sjobs[i].getDuration();
				int j = i +1;
				while(j < nbJobs && sjobs[j].getDuration() == d) {
					if(	sjobs[i].getDeadline() <= sjobs[j].getDeadline() && 
							sjobs[i].getDueDate() <= sjobs[j].getDueDate()) {
						// i precedes j
						final int ti = sjobs[i].getID();
						final int tj = sjobs[j].getID();
						model.addConstraint(precedence(tasks[ti], tasks[tj], setupTimes[ti][tj]));
					}
					j++;
				}
			}
		}
		return model;
	}


	@Override
	protected void setGoals(PreProcessCPSolver solver) {
		super.setGoals(solver);
		solver.addGoal(new LexMaxFakeBranching(solver, solver.getVar(dueDates)));		
	}

	//Ugly but quick
	private final class VarValPairSelWrapper implements VarValPairSelector {

		VarValPairSelector internal;
		
		public void init() {
			internal = (VarValPairSelector) solver.getCstr(relaxConstraint);
		}
		
		@Override
		public IntVarValPair selectVarValPair() throws ContradictionException {
			return internal.selectVarValPair();
		}
		
		
	}
	
	private final VarValPairSelWrapper VVPSWrapper = new VarValPairSelWrapper();
		
	@Override
	protected AbstractIntBranchingStrategy makeUserDisjunctBranching(
			PreProcessCPSolver solver, long seed) {
		if(SingleMachineSettings.readPmtnLevel(this).isOn()) {
		return new AssignOrForbidIntVarValPair(VVPSWrapper);
		} else {
			logMsg.appendConfiguration(SchedulingBranchingFactory.Branching.LEX + " OVERLOAD_BRANCHING");
			return lexicographic(solver, getBoolDecisionVars(solver));
		}
	}


	@Override
	public Solver buildSolver() {
		CPSolver s = (CPSolver) super.buildSolver();

		//		if(SingleMachineSettings.stateRelaxationConstraint(this)) {
		//			////////////
		//			//Add relaxation constraint
		//			RelaxLmaxConstraint.canFailOnSolutionRecording = DisjunctiveSettings.getBranching(s.getConfiguration()) == SchedulingBranchingFactory.Branching.ST;
		//			// FIXME - Awful : can not really postponed until the disjunctive model is built - created 10 avr. 2012 by A. Malapert
		//			s.addConstraint(
		//					new ComponentConstraint(RelaxLmaxManager.class, 
		//							this, 
		//							ArrayUtils.append(tasks, dueDates, new IntegerVariable[]{objVar})));				
		//		}
		if( defaultConf.readBoolean(SingleMachineSettings.TASK_ORDERING) && ! hasSetupTimes() ) {
			//Escape from a bug in the preprocessing by :
			// instantiating variable replaced (but unfortunatly already created) during the preprocessing)
			try {
				for (int i = 0; i < s.getNbIntVars(); i++) {
					final IntDomainVar v = s.getIntVarQuick(i);
					if(v.getNbConstraints() == 0) {
						v.instantiate(v.getInf(), null, false);
					}
				}
			} catch (ContradictionException e) {
				throw new ModelException("Preprocessing Patch Failed !! ");
			}
		}
		return s;
	}


	@Override
	public Boolean solve() {
		//Print initial propagation		

		solver.getSearchStrategy().initialPropagation();
		if(solver.isFeasible() == Boolean.FALSE) return Boolean.FALSE;
		//VisuFactory.getDotManager().show(((PreProcessCPSolver) solver).getDisjSModel());
		//LOGGER.info(solver.pretty());
		if(SingleMachineSettings.stateRelaxationConstraint(this)) {
			////////////
			//Add relaxation constraint
			RelaxLmaxConstraint.canFailOnSolutionRecording = DisjunctiveSettings.getBranching(solver.getConfiguration()) == SchedulingBranchingFactory.Branching.ST;
			relaxConstraint = new ComponentConstraint(RelaxLmaxManager.class, 
					this, 
					ArrayUtils.append(tasks, dueDates, new IntegerVariable[]{objVar}));		
			// FIXME - Awful : can not really postponed until the disjunctive model is built - created 10 avr. 2012 by A. Malapert
			//More awful : all other constraints must be awaken before the relaxation.
			//The only advantage build a better precedence graph
			((CPSolver) solver).addConstraint(relaxConstraint);
			VVPSWrapper.init();
		}
		return super.solve();
	}

	protected double getGapILB() {
		final int maxDueDate = maxDueDate(jobs);
		return ( objective.doubleValue() + maxDueDate) / ( getComputedLowerBound()+ maxDueDate);
	}

	@Override
	protected Object makeSolutionChart() {
		return solver != null && solver.existsSolution() ?
				ChocoChartFactory.createGanttChart("", solver.getVar(tasks), dueDates(jobs)) : null;
	}



}
