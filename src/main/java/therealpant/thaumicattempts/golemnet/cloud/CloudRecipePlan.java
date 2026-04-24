package therealpant.thaumicattempts.golemnet.cloud;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CloudRecipePlan {
    private final List<CloudItemRequest> inputs = new ArrayList<>();
    private final List<CloudItemRequest> outputs = new ArrayList<>();

    public List<CloudItemRequest> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

    public List<CloudItemRequest> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }

    public void addInput(CloudItemRequest request) {
        if (request != null) inputs.add(request);
    }

    public void addOutput(CloudItemRequest request) {
        if (request != null) outputs.add(request);
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList in = new NBTTagList();
        for (CloudItemRequest request : inputs) {
            in.appendTag(request.serializeNBT());
        }
        NBTTagList out = new NBTTagList();
        for (CloudItemRequest request : outputs) {
            out.appendTag(request.serializeNBT());
        }
        nbt.setTag("inputs", in);
        nbt.setTag("outputs", out);
        return nbt;
    }

    public static CloudRecipePlan deserializeNBT(NBTTagCompound nbt) {
        CloudRecipePlan plan = new CloudRecipePlan();
        if (nbt == null) return plan;

        NBTTagList in = nbt.getTagList("inputs", 10);
        for (int i = 0; i < in.tagCount(); i++) {
            plan.addInput(CloudItemRequest.deserializeNBT(in.getCompoundTagAt(i)));
        }
        NBTTagList out = nbt.getTagList("outputs", 10);
        for (int i = 0; i < out.tagCount(); i++) {
            plan.addOutput(CloudItemRequest.deserializeNBT(out.getCompoundTagAt(i)));
        }
        return plan;
    }
}