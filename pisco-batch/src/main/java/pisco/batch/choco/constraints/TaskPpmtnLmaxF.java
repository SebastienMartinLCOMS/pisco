/**
*  Copyright (c) 2011, Arnaud Malapert
*  All rights reserved.
*  Redistribution and use in source and binary forms, with or without
*  modification, are permitted provided that the following conditions are met:
*
*      * Redistributions of source code must retain the above copyright
*        notice, this list of conditions and the following disclaimer.
*      * Redistributions in binary form must reproduce the above copyright
*        notice, this list of conditions and the following disclaimer in the
*        documentation and/or other materials provided with the distribution.
*      * Neither the name of the Arnaud Malapert nor the
*        names of its contributors may be used to endorse or promote products
*        derived from this software without specific prior written permission.
*
*  THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
*  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
*  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
*  DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
*  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
*  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
*  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
*  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
*  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
*  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package pisco.batch.choco.constraints;

import choco.kernel.common.logging.ChocoLogging;
import choco.kernel.solver.ContradictionException;

public final class TaskPpmtnLmaxF extends AbstractTaskPList {

	private final ParallelLmaxFlowGraph flowgraph;

	public TaskPpmtnLmaxF(PBatchRelaxSConstraint cstr, boolean singleAndParallel) {
		super(cstr, singleAndParallel);
		flowgraph = new ParallelLmaxFlowGraph(cstr.data.getDueDates(), cstr.data.getCapacity());
	}


	@Override
	public void reset() {
		super.reset();
		flowgraph.reset();
	}

	@Override
	protected void filterParallelMachines() throws ContradictionException {
		final int n = parallelSize();
		if( n > 0 ) {
			//VisuFactory.getDotManager().show(flowgraph);
			flowgraph.setMaximalLateness(cstr.getObjSup());
			int expectedMaxFlow = 0;
			for (int i = 0; i < n; i++) {
				expectedMaxFlow += taskPList[i].getDuration() * taskPList[i].getSize();
				flowgraph.addJob(taskPList[i]);	
			}
			flowgraph.close();
			final int maxFlow = flowgraph.edmondsKarp();
			if(expectedMaxFlow > maxFlow) {
				//VisuFactory.getDotManager().show(flowgraph);
				//ChocoLogging.getBranchingLogger().info("FAIL");
				//FIXME cstr.fail();
			}
			//assert(expectedMaxFlow == maxFlow && maxFlow == flowgraph.computeSourceCutCapacity());
		}
	}

}
