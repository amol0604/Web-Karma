package edu.isi.karma.controller.update;

import java.io.PrintWriter;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.importdata.ImportSQLCommand;
import edu.isi.karma.view.VWorkspace;

public class SQLCommandUpdate extends AbstractUpdate {

	private Command command;
	private static Logger logger = LoggerFactory.getLogger(SQLCommandUpdate.class);
	
	public enum JsonKeys {
		commandId
	}

	public SQLCommandUpdate(ImportSQLCommand command) {
		this.command = command;
	}

	@Override
	public void generateJson(String prefix, PrintWriter pw,
			VWorkspace vWorkspace) {
		JSONObject responseObj = new JSONObject();
		try {
			responseObj.put(JsonKeys.commandId.name(), command.getId());
			responseObj.put(GenericJsonKeys.updateType.name(), "ImportSQLCommandUpdate");
			pw.print(responseObj.toString(4));
		} catch (JSONException e) {
			logger.error("Error generating JSON!", e);
		}
	}
}
