package de.zalando.typemapper.core.fieldMapper;

import java.lang.reflect.InvocationTargetException;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.zalando.typemapper.annotations.DatabaseField;
import de.zalando.typemapper.core.Mapping;
import de.zalando.typemapper.core.result.ArrayResultNode;
import de.zalando.typemapper.core.result.DbResultNode;
import de.zalando.typemapper.core.result.DbResultNodeType;
import de.zalando.typemapper.core.result.ObjectResultNode;
import de.zalando.typemapper.exception.NotsupportedTypeException;

public class ObjectFieldMapper {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectFieldMapper.class);

    private static boolean isRowWithAllFieldsNull(final ObjectResultNode node) {
        if (node != null && node.getChildren() != null) {
            for (DbResultNode dbResultNode : node.getChildren()) {
                if (dbResultNode != null && dbResultNode.getValue() != null) {
                    return false;
                }
            }
        }

        return true;
    }

    public static final Object mapFromDbObjectNode(final Class classz, final ObjectResultNode node,
            final Mapping mapping) throws InstantiationException, IllegalAccessException, IllegalArgumentException,
        InvocationTargetException, NotsupportedTypeException {
        DatabaseField databaseField = mapping.getField().getAnnotation(DatabaseField.class);
        final Object value;

        if (mapping.isOptionalField() && isRowWithAllFieldsNull(node)) {
            value = null;
        } else if (databaseField.mapper() != DefaultObjectMapper.class) {
            ObjectMapper mapper = databaseField.mapper().newInstance();
            value = mapper.unmarshalFromDbNode(node);
        } else {
            value = mapField(mapping.getFieldClass(), node);
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    public static final Object mapField(@SuppressWarnings("rawtypes") final Class clazz, final ObjectResultNode node)
        throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
            NotsupportedTypeException {
        if (node.getChildren() == null) {
            return null;
        }

        Object result = null;

        // instantiate enums by string value
        if (clazz.isEnum()) {
            final DbResultNode currentNode = node.getChildByName(node.getType());
            result = Enum.valueOf(clazz, currentNode.getValue());
        } else {
            result = clazz.newInstance();

            final List<Mapping> mappings = Mapping.getMappingsForClass(clazz);

            for (final Mapping mapping : mappings) {
                final DbResultNode currentNode = node.getChildByName(mapping.getName());
                if (currentNode == null) {
                    if (mapping.isOptionalField()) {
                        mapping.map(result, null);
                    } else {
                        LOG.warn("Could not find value of mapping: {}", mapping.getName());
                    }

                    continue;
                }

                // TODO pribeiro. We should use polymorphism instead. Build like a chain.
                try {
                    if (DbResultNodeType.SIMPLE.equals(currentNode.getNodeType())) {
                        mapping.map(result,
                            mapping.getFieldMapper().mapField(currentNode.getValue(), mapping.getFieldClass()));
                    } else if (DbResultNodeType.OBJECT.equals(currentNode.getNodeType())) {
                        mapping.map(result, mapFromDbObjectNode(clazz, (ObjectResultNode) currentNode, mapping));
                    } else if (DbResultNodeType.ARRAY.equals(currentNode.getNodeType())) {
                        mapping.map(result,
                            ArrayFieldMapper.mapField(mapping.getField(), (ArrayResultNode) currentNode));
                    }
                } catch (final Exception e) {
                    LOG.error("Failed to map property {} of class {}",
                        new Object[] {mapping.getName(), clazz.getSimpleName(), e});
                }
            }
        }

        return result;
    }

}
