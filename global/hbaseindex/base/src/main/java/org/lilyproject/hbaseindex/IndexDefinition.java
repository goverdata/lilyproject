/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilyproject.hbaseindex;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.gotometrics.orderly.Order;
import com.gotometrics.orderly.RowKey;
import com.gotometrics.orderly.StructBuilder;
import com.gotometrics.orderly.StructRowKey;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.Writable;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;

/**
 * Defines the structure of an index.
 *
 * <p>An index is defined by instantiating an object of this class, adding one
 * or more fields to it using the methods like {@link #addStringField},
 * {@link #addIntegerField}, etc. Finally the index is created by calling
 * {@link IndexManager#getIndex}. After creation, the definition of an index
 * cannot be modified.
 */
public class IndexDefinition implements Writable {
    public static final byte[] DATA_FAMILY = Bytes.toBytes("data");

    private String name;
    private List<IndexFieldDefinition> fields = new ArrayList<IndexFieldDefinition>();
    private final Map<String, IndexFieldDefinition> fieldsByName = new HashMap<String, IndexFieldDefinition>();
    private IndexFieldDefinition identifierIndexFieldDefinition;

    public IndexDefinition() {
        // for hadoop serialization
    }

    public IndexDefinition(String name) {
        Preconditions.checkNotNull(name, "Null argument: name");

        this.name = name;
        setIdentifierOrder(Order.ASCENDING);
    }

    public IndexDefinition(String name, ObjectNode jsonObject) {
        this.name = name;

        if (jsonObject.get("identifierOrder") != null) {
            setIdentifierOrder(Order.valueOf(jsonObject.get("identifierOrder").getTextValue()));
        } else {
            setIdentifierOrder(Order.ASCENDING);
        }

        try {
            ObjectNode fields = (ObjectNode) jsonObject.get("fields");
            Iterator<Map.Entry<String, JsonNode>> fieldsIt = fields.getFields();
            while (fieldsIt.hasNext()) {
                Map.Entry<String, JsonNode> entry = fieldsIt.next();
                String className = entry.getValue().get("class").getTextValue();
                Class<IndexFieldDefinition> clazz =
                        (Class<IndexFieldDefinition>) getClass().getClassLoader().loadClass(className);
                Constructor<IndexFieldDefinition> constructor = clazz.getConstructor(String.class, ObjectNode.class);
                IndexFieldDefinition field = constructor.newInstance(entry.getKey(), entry.getValue());
                add(field);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error instantiating IndexDefinition.", e);
        }
    }

    public String getName() {
        return name;
    }

    public void setIdentifierOrder(Order identifierOrder) {
        Preconditions.checkNotNull(identifierOrder, "Null argument: identifierOrder");
        this.identifierIndexFieldDefinition = new VariableLengthByteIndexFieldDefinition("identifier");
        this.identifierIndexFieldDefinition.setOrder(identifierOrder);
    }

    public IndexFieldDefinition getField(String name) {
        return fieldsByName.get(name);
    }

    public IndexFieldDefinition getIdentifierIndexFieldDefinition() {
        return identifierIndexFieldDefinition;
    }

    public StringIndexFieldDefinition addStringField(String name) {
        validateName(name);
        StringIndexFieldDefinition definition = new StringIndexFieldDefinition(name);
        add(definition);
        return definition;
    }

    public IntegerIndexFieldDefinition addIntegerField(String name) {
        validateName(name);
        IntegerIndexFieldDefinition definition = new IntegerIndexFieldDefinition(name);
        add(definition);
        return definition;
    }

    public FloatIndexFieldDefinition addFloatField(String name) {
        validateName(name);
        FloatIndexFieldDefinition definition = new FloatIndexFieldDefinition(name);
        add(definition);
        return definition;
    }

    public DecimalIndexFieldDefinition addDecimalField(String name) {
        validateName(name);
        DecimalIndexFieldDefinition definition = new DecimalIndexFieldDefinition(name);
        add(definition);
        return definition;
    }

    public LongIndexFieldDefinition addLongField(String name) {
        validateName(name);
        LongIndexFieldDefinition definition = new LongIndexFieldDefinition(name);
        add(definition);
        return definition;
    }

    public VariableLengthByteIndexFieldDefinition addVariableLengthByteField(String name, int fixedPrefixLength) {
        validateName(name);
        final VariableLengthByteIndexFieldDefinition definition =
                new VariableLengthByteIndexFieldDefinition(name, fixedPrefixLength);
        add(definition);
        return definition;
    }

    public VariableLengthByteIndexFieldDefinition addVariableLengthByteField(String name) {
        validateName(name);
        final VariableLengthByteIndexFieldDefinition definition =
                new VariableLengthByteIndexFieldDefinition(name);
        add(definition);
        return definition;
    }

    public ByteIndexFieldDefinition addByteField(String name, int lengthInBytes) {
        validateName(name);
        ByteIndexFieldDefinition definition = new ByteIndexFieldDefinition(name, lengthInBytes);
        add(definition);
        return definition;
    }

    private IndexFieldDefinition add(IndexFieldDefinition fieldDef) {
        fields.add(fieldDef);
        fieldsByName.put(fieldDef.getName(), fieldDef);
        return fieldDef;
    }

    private void validateName(String name) {
        Preconditions.checkNotNull(name, "Null argument: name");
        if (fieldsByName.containsKey(name)) {
            throw new IllegalArgumentException("Field name already exists in this IndexDefinition: " + name);
        }
    }

    public List<IndexFieldDefinition> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public StructRowKey asStructRowKey() {
        final StructBuilder structBuilder = new StructBuilder();

        // add all fields
        for (IndexFieldDefinition field : fields) {
            structBuilder.add(field.asRowKey());
        }

        // add identifier
        structBuilder.add(this.identifierIndexFieldDefinition.asRowKey());

        return structBuilder.toRowKey();
    }

    /**
     * Get the position of a given field in the index definition.
     *
     * @param fieldName name of the field to look for
     * @return position in the index, or -1 if the field is not part of the index
     */
    public int getFieldPosition(String fieldName) {
        int pos = 0;
        for (IndexFieldDefinition field : fields) {
            if (field.getName().equals(fieldName)) {
                return pos;
            }
            pos++;
        }

        return -1;
    }

    /**
     * Check if the index definition would support storing the given field with the given value.
     *
     * @param fieldName  name of the field to be stored in the index
     * @param fieldValue value to be stored under this name
     * @throws MalformedIndexEntryException if the given field is not supported
     */
    public void checkFieldSupport(String fieldName, Object fieldValue) {
        final IndexFieldDefinition correspondingIndexFieldDefinition = fieldsByName.get(fieldName);
        if (correspondingIndexFieldDefinition == null) {
            throw new MalformedIndexEntryException("Index entry contains a field that is not part of " +
                    "the index definition: " + fieldName);

        } else if (fieldValue != null) {
            final RowKey rowKey = correspondingIndexFieldDefinition.asRowKey();
            if (!rowKey.getDeserializedClass().isAssignableFrom(fieldValue.getClass())) {
                throw new MalformedIndexEntryException("Index entry for field " + fieldName + " contains" +
                        " a value of an incorrect type. Expected: " + rowKey.getDeserializedClass() +
                        ", found: " + fieldValue.getClass().getName());
            }
        }
    }

    public ObjectNode toJson() {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode object = factory.objectNode();
        ObjectNode fieldsJson = object.putObject("fields");

        for (IndexFieldDefinition field : fields) {
            fieldsJson.put(field.getName(), field.toJson());
        }

        object.put("identifierOrder", this.identifierIndexFieldDefinition.getOrder().toString());

        return object;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        IndexDefinition other = (IndexDefinition) obj;

        if (!name.equals(other.name)) {
            return false;
        }

        if (identifierIndexFieldDefinition != other.identifierIndexFieldDefinition) {
            return false;
        }

        if (!fields.equals(other.fields)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (fields != null ? fields.hashCode() : 0);
        result = 31 * result + (identifierIndexFieldDefinition != null ? identifierIndexFieldDefinition.hashCode() : 0);
        return result;
    }

    @Override
    public void write(DataOutput out) throws IOException {
/*
    private final String name;
    private final List<IndexFieldDefinition> fields = new ArrayList<IndexFieldDefinition>();
    private final Map<String, IndexFieldDefinition> fieldsByName = new HashMap<String, IndexFieldDefinition>();
    private IndexFieldDefinition identifierIndexFieldDefinition;

 */
        out.writeUTF(name);
        out.writeInt(fields.size());
        for (IndexFieldDefinition field : fields) {
            out.writeUTF(field.getClass().getName());
            field.write(out);
        }
        out.writeUTF(identifierIndexFieldDefinition.getClass().getName());
        identifierIndexFieldDefinition.write(out);

    }

    @Override
    public void readFields(DataInput in) throws IOException {
        name = in.readUTF();
        final int fieldsSize = in.readInt();
        fields = new ArrayList<IndexFieldDefinition>(fieldsSize);
        for (int i = 0; i < fieldsSize; i++) {
            final String indexFieldDefinitionClassName = in.readUTF();
            final IndexFieldDefinition indexFieldDefinition =
                    (IndexFieldDefinition) tryInstantiateClass(indexFieldDefinitionClassName);
            indexFieldDefinition.readFields(in);
            fields.add(indexFieldDefinition);
        }

        final String identifierIndexFieldDefinitionClassName = in.readUTF();
        identifierIndexFieldDefinition =
                (IndexFieldDefinition) tryInstantiateClass(identifierIndexFieldDefinitionClassName);
        identifierIndexFieldDefinition.readFields(in);
        refreshFieldsByName();
    }

    private Object tryInstantiateClass(String className) throws IOException {
        try {
            return Class.forName(className).newInstance();
        } catch (InstantiationException e) {
            throw new IOException(e);
        } catch (IllegalAccessException e) {
            throw new IOException(e);
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    private void refreshFieldsByName() {
        this.fieldsByName.clear();
        for (IndexFieldDefinition field : fields) {
            fieldsByName.put(field.getName(), field);
        }
    }

}
