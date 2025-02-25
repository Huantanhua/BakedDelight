package net.djahmo.bakeddelight.custom.block;

import net.djahmo.bakeddelight.custom.entity.BakingDishEntity;
import net.djahmo.bakeddelight.custom.item.FoodItem;
import net.djahmo.bakeddelight.custom.recipe.BakingDishRecipe;
import net.djahmo.bakeddelight.registry.ModBlocks;
import net.djahmo.bakeddelight.registry.ModRecipes;
import net.djahmo.bakeddelight.utils.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class BakingDishBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING;
    public static final IntegerProperty SLICE;
    public static final IntegerProperty DISH;

    public static ArrayList<ArrayList<Item>> recipesList;

    protected static ArrayList<ArrayList<Item>> validRecipes = new ArrayList<>();

    public static final int maxSlice = 6;

    public BakingDishBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(SLICE, 0));
    }

    public static BlockState getBakingDish(BlockState state) {
        return ModBlocks.BAKING_DISH.get().defaultBlockState().setValue(FACING, state.getValue(FACING));
    }

    @Override
    public BlockState updateShape(BlockState stateIn, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        return facing == Direction.DOWN && !stateIn.canSurvive(level, currentPos) ? Blocks.AIR.defaultBlockState() : super.updateShape(stateIn, facing, facingState, level, currentPos, facingPos);
    }
    @Override
    public int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos pos) {
        return maxSlice - blockState.getValue(SLICE);
    }
    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
        switch (blockState.getValue(FACING)) {
            case NORTH, SOUTH -> {
                return Block.box(1,0,3,15,7.5,13);
            }
            case EAST, WEST -> {
                return Block.box(3,0,1,13,7.5,15);
            }
        }
        return null;
    }

    @Override
    public BlockState getStateForPlacement( BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }

    @Override
    public BlockState mirror( BlockState pState, Mirror pMirror) {
        return pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SLICE, DISH);
    }

    static {
        FACING = BlockStateProperties.HORIZONTAL_FACING;
        SLICE = IntegerProperty.create("slice", 0, 5);
        DISH = IntegerProperty.create("dish", 1, 4);
    }

    /* BLOCK ENTITY */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldStack = player.getItemInHand(hand);
        if(!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            BlockEntity entity = level.getBlockEntity(pos);
            if(entity instanceof BakingDishEntity) {
                if(!player.isCrouching())
                    return addSlice(state, level, pos, heldStack, player, (BakingDishEntity)entity);
                else
                    return dropSlice(state, level, pos, heldStack, (BakingDishEntity)entity);
            } else {
                throw new IllegalStateException("BakingDishEntity is missing");
            }
        }
        return InteractionResult.PASS;
    }

    private boolean verify(int slice, Item item, BakingDishEntity entity, boolean add) {
        if (slice == 0) {
            return initializeValidRecipes(item);
        } else {
            return add ? updateValidRecipes(slice, item) : resetAndValidateRecipes(entity);
        }
    }

    private boolean initializeValidRecipes(Item item) {
        validRecipes.clear();
        boolean found = false;
        for (ArrayList<Item> recipe : recipesList) {
            if (recipe.get(1).equals(item)) {
                found = true;
                validRecipes.add(recipe);
            }
        }
        return found;
    }

    private boolean updateValidRecipes(int slice, Item item) {
        ArrayList<ArrayList<Item>> matchedRecipes = new ArrayList<>();
        boolean found = false;
        for (ArrayList<Item> recipe : validRecipes) {
            if (recipe.get(slice + 1).equals(item)) {
                found = true;
                matchedRecipes.add(recipe);
            }
        }
        if (found) {
            validRecipes = matchedRecipes;
        }
        return found;
    }

    private boolean resetAndValidateRecipes(BakingDishEntity entity) {
        validRecipes.clear();
        ArrayList<Item> list = entity.getItemList();
        for (ArrayList<Item> recipe : recipesList) {
            if (isRecipeValid(list, recipe)) {
                validRecipes.add(recipe);
            }
        }
        return !validRecipes.isEmpty();
    }

    private boolean isRecipeValid(ArrayList<Item> list, ArrayList<Item> recipe) {
        for (int i = 0; i < list.size(); i++) {
            if (!recipe.get(i + 1).equals(list.get(i))) {
                return false;
            }
        }
        return true;
    }

    protected InteractionResult addSlice(BlockState state, Level level, BlockPos pos, ItemStack heldStack, Player player, BakingDishEntity entity) {
        if(recipesList == null)
            recipesList = ModRecipes.getAllRecipes(BakingDishRecipe.Type.INSTANCE);

        int slice = state.getValue(SLICE);
        boolean found = verify(slice, heldStack.getItem(), entity, true);
        if(found) {
            if(slice < maxSlice-1) {
                Integer type = BackingDishTypeCollection.getDishId(Function.getItemName(validRecipes.get(0).get(0)));
                if(type == null)
                    throw new IllegalStateException("Type is missing");
                if(heldStack.getItem() instanceof FoodItem) {
                    Item toDrop = ((FoodItem) heldStack.getItem()).remaining;
                    if(toDrop!= null)
                        player.getInventory().add(new ItemStack(toDrop));
                }
                player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
                entity.addItem(new ItemStack(heldStack.getItem()), slice);
                level.setBlock(pos, state.setValue(SLICE, slice+1).setValue(DISH, type), Block.UPDATE_ALL);
                return InteractionResult.SUCCESS;
            }
            else {
                player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
                level.setBlock(pos, Block.byItem(validRecipes.get(0).get(0)).defaultBlockState().setValue(FACING, state.getValue(FACING)), Block.UPDATE_ALL);
            }
        }
        return InteractionResult.PASS;
    }

    protected InteractionResult dropSlice(BlockState state, Level level, BlockPos pos, ItemStack heldStack, BakingDishEntity entity) {
        int slice = state.getValue(SLICE);
        if(slice > 0) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), entity.dropItem(slice-1));
            level.setBlock(pos, state.setValue(SLICE, slice-1), Block.UPDATE_ALL);
            verify(slice, heldStack.getItem(), entity, false);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if(validRecipes != null && state.getValue(SLICE) > 0) {
            if(state.getBlock() != newState.getBlock() && newState.getBlock() != Block.byItem(validRecipes.get(0).get(0))){
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if(blockEntity instanceof BakingDishEntity){
                    ((BakingDishEntity) blockEntity).drops();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BakingDishEntity(pos, state);
    }
}
