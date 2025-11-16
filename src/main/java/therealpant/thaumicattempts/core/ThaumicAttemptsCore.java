package therealpant.thaumicattempts.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

import static therealpant.thaumicattempts.ThaumicAttempts.MODID;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("ThaumicAttemptsCore")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({
        "therealpant.thaumicattempts.core"
})
public class ThaumicAttemptsCore implements IFMLLoadingPlugin {

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public ThaumicAttemptsCore() {
        System.out.println("[ThaumicAttemptsCore] Coremod constructed");
    }

    @Override
    public String[] getASMTransformerClass() {
        System.out.println("[ThaumicAttemptsCore] Registering transformer ThaumicAttemptsTransformer");
        return new String[] {
                "therealpant.thaumicattempts.core.ThaumicAttemptsTransformer"
        };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // no-op
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
