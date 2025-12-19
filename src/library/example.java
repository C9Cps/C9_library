package library;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.world.Block;

import static mindustry.type.ItemStack.with;

public class example {
    public static Block
            DirectNode, HeatingReactor;
    public static void load() {
        DirectNode = new HeatingReactor("dir-node"){{
            requirements(Category.power, with(Items.copper, 6));
        }};
    }
}
