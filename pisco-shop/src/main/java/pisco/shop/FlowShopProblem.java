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
/**
 *
 */
package pisco.shop;

import java.io.File;

import parser.absconparseur.tools.UnsupportedConstraintException;
import parser.instances.BasicSettings;
import pisco.shop.heuristics.BPrecedence;
import pisco.shop.heuristics.BTask;
import pisco.shop.heuristics.IBPrecFactory;
import pisco.shop.parsers.FlowShopParser;
import choco.Choco;
import choco.cp.model.CPModel;
import choco.kernel.model.Model;
import choco.kernel.model.variables.scheduling.TaskVariable;
import choco.kernel.solver.variables.scheduling.TaskVar;

class BprecFactoryFS  implements IBPrecFactory {

	class BPrecFS extends BPrecedence {

		public BPrecFS(BTask t1, BTask t2) {
			super(t1, t2);
		}

		@Override
		public boolean isChecked() {
			return isSameJob();
		}

		@Override
		public boolean isSatisfied() {
			return isSameJob() && t1.machine<t2.machine;
		}
	}

	@Override
	public BPrecedence makeBPrecedence(BTask t1, BTask t2) {
		return new BPrecFS(t1,t2);
	}
}


/**
 * @author Arnaud Malapert
 *
 */
public class FlowShopProblem extends GenericShopProblem {

	public final static IBPrecFactory FACTORY = new BprecFactoryFS();

	public FlowShopProblem(BasicSettings settings) {
		super(new FlowShopParser(), settings);
	}



	@Override
	public void load(File fichier) throws UnsupportedConstraintException {
		super.load(fichier);
		getCrashHeuristics().setFactory(FACTORY);
	}



	@Override
	public Model buildModel() {
		CPModel model = (CPModel) super.buildModel();
		addMachineResources(model);
		//operations of a job are ordered on machines
		for (int j = 0; j < nbJobs; j++) {
			for (int m = 1; m < nbMachines; m++) {
				TaskVariable t1 = tasks[m-1][j];
				TaskVariable t2 = tasks[m][j];
				model.addConstraint(Choco.startsAfterEnd(t2,t1));
			}
		}
		return model;

	}




}
