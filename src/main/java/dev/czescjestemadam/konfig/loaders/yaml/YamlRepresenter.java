package dev.czescjestemadam.konfig.loaders.yaml;

import dev.czescjestemadam.konfig.loaders.ConfigNode;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.representer.StandardRepresenter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class YamlRepresenter extends StandardRepresenter {
	public YamlRepresenter(DumpSettings dumpSettings) {
		super(dumpSettings);
		representers.put(ConfigNode.class, data -> representData(((ConfigNode)data).data()));
	}

	@Override
	protected NodeTuple representMappingEntry(Map.Entry<?, ?> entry) {
		if (!(entry.getValue() instanceof ConfigNode configNode)) {
			return super.representMappingEntry(entry);
		}

		final Node keyNode = representData(entry.getKey());
		final Node valueNode = representData(configNode.data());

		if (configNode.comments() != null) {
			keyNode.setBlockComments(Arrays.stream(configNode.comments())
				.map(comment -> new CommentLine(
					Optional.empty(),
					Optional.empty(),
					comment,
					CommentType.BLOCK
				))
				.toList());
		}

		return new NodeTuple(keyNode, valueNode);
	}
}
