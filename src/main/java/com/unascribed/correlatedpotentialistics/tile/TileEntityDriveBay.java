package com.unascribed.correlatedpotentialistics.tile;

import com.google.common.base.Predicates;
import com.unascribed.correlatedpotentialistics.helper.ItemStacks;
import com.unascribed.correlatedpotentialistics.item.ItemDrive;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

public class TileEntityDriveBay extends TileEntityNetworkMember implements ITickable {

	private ItemStack[] drives = new ItemStack[8];
	private int consumedPerTick = 8;

	@Override
	public void writeToNBT(NBTTagCompound compound) {
		super.writeToNBT(compound);
		writeDrives(compound, 0, 1, 2, 3, 4, 5, 6, 7);
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);
		readDrives(compound);
	}

	@Override
	public int getEnergyConsumedPerTick() {
		return consumedPerTick;
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		writeDrives(nbt, 0, 1, 2, 3, 4, 5, 6, 7);
		return new S35PacketUpdateTileEntity(getPos(), getBlockMetadata(), nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		readDrives(pkt.getNbtCompound());
	}

	@Override
	public void update() {
		if (hasWorldObj() && !worldObj.isRemote) {
			for (int i = 0; i < 8; i++) {
				ItemStack is = drives[i];
				if (is == null) continue;
				if (ItemStacks.getBoolean(is, "Dirty").or(false)) {
					is.getTagCompound().removeTag("Dirty");
					markDirty();
					setDriveInSlot(i, is);
				}
			}
		}
	}

	private void writeDrives(NBTTagCompound nbt, int... slots) {
		for (int i : slots) {
			writeDrive(nbt, i);
		}
	}

	private void writeDrive(NBTTagCompound nbt, int slot) {
		NBTTagCompound drive = new NBTTagCompound();
		if (drives[slot] != null) {
			drives[slot].writeToNBT(drive);
		}
		nbt.setTag("Drive"+slot, drive);
	}

	private void readDrives(NBTTagCompound nbt) {
		for (int i = 0; i < drives.length; i++) {
			if (nbt.hasKey("Drive"+i)) {
				NBTTagCompound drive = nbt.getCompoundTag("Drive"+i);
				if (drive.hasNoTags()) {
					drives[i] = null;
				} else {
					ItemStack is = ItemStack.loadItemStackFromNBT(drive);
					if (hasWorldObj() && worldObj.isRemote) {
						ItemStacks.ensureHasTag(is);
						is.setTagCompound((NBTTagCompound)is.getTagCompound().copy());
						is.getTagCompound().setBoolean("Dirty", true);
					}
					drives[i] = is;
				}
			}
		}
		onDriveChange();
	}

	public void setDriveInSlot(int slot, ItemStack drive) {
		drives[slot] = drive;
		if (hasWorldObj() && !worldObj.isRemote && worldObj instanceof WorldServer) {
			NBTTagCompound nbt = new NBTTagCompound();
			writeDrive(nbt, slot);
			WorldServer ws = (WorldServer)worldObj;
			Chunk c = worldObj.getChunkFromBlockCoords(getPos());
			S35PacketUpdateTileEntity packet = new S35PacketUpdateTileEntity(getPos(), getBlockMetadata(), nbt);
			for (EntityPlayerMP player : worldObj.getPlayers(EntityPlayerMP.class, Predicates.alwaysTrue())) {
				if (ws.getPlayerManager().isPlayerWatchingChunk(player, c.xPosition, c.zPosition)) {
					player.playerNetServerHandler.sendPacket(packet);
				}
			}
			onDriveChange();
		}
	}

	private void onDriveChange() {
		int old = consumedPerTick;
		consumedPerTick = 8;
		for (ItemStack is : drives) {
			if (is == null) continue;
			if (is.getItem() instanceof ItemDrive) {
				consumedPerTick += ((ItemDrive)is.getItem()).getRFConsumptionRate(is);
			}
		}
		if (hasWorldObj() && !worldObj.isRemote && hasController()) {
			getController().updateConsumptionRate(consumedPerTick-old);
			getController().updateDrivesCache();
		}
	}

	public ItemStack getDriveInSlot(int slot) {
		return drives[slot];
	}

	public boolean hasDriveInSlot(int slot) {
		return drives[slot] != null;
	}

}
