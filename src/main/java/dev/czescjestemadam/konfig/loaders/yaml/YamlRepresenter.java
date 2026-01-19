package dev.czescjestemadam.konfig.loaders.yaml;

import dev.czescjestemadam.konfig.loaders.ConfigNode;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.representer.StandardRepresenter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class YamlRepresenter extends StandardRepresenter {
	public YamlRepresenter(DumpSettings dumpSettings) {
		super(dumpSettings);
		representers.put(ConfigNode.class, this::representConfigNode);
	}

	@Override
	public Node represent(Object obj) {
		final Node node = super.represent(obj);
		if (!(node instanceof MappingNode mappingNode) || !(obj instanceof Map<?, ?> data)) {
			return node;
		}

		for (final NodeTuple tuple : mappingNode.getValue()) {
			final Node keyNode = tuple.getKeyNode();
			if (!(keyNode instanceof ScalarNode keyScalarNode)) {
				continue;
			}

			if (!(data.get(keyScalarNode.getValue()) instanceof ConfigNode configNode)) {
				continue;
			}

			if (configNode.comments() != null) {
				keyScalarNode.setBlockComments(Arrays.stream(configNode.comments())
						.map(comment -> new CommentLine(
								Optional.empty(),
								Optional.empty(),
								comment,
								CommentType.BLOCK
						))
						.toList());
			}
		}

		return node;
	}

	private Node representConfigNode(Object data) {
		return represent(((ConfigNode)data).data());
	}
}
