/**
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Copyright 2011-2017 Peter Güttinger and contributors
 */
package ch.njol.skript.expressions;

import java.lang.reflect.Array;
import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.eclipse.jdt.annotation.Nullable;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Events;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.entity.EntityData;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.log.ErrorQuality;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;

/**
 * @author Peter Güttinger
 */
@Name("Clicked Block/Entity")
@Description("The clicked block or entity - only useful in click events")
@Examples({"message \"You clicked on a %type of clicked entity%!\"",
		"clicked block is a chest:",
		"	show the inventory of the clicked block to the player"})
@Since("1.0")
@Events("click")
public class ExprClicked extends SimpleExpression<Object> {

	private static enum ClickableType {
		
		BLOCK_AND_ITEMS(1, Block.class, "clicked block/itemtype/entity", "(block|%-*itemtype/entitydata%)"),
		SLOT(2, Number.class, "clicked slot", "[raw] slot"),
		INVENTORY(3, Inventory.class, "clicked inventory", "inventory"),
		TYPE(4, ClickType.class, "clicked action", "action"), //Clicked type = Clicked action (ClickType.class) in Skript.
		ACTION(5, InventoryAction.class, "clicked inventory action", "inventory( |-)action"); //Clicked inventory action = InventoryAction.class.
		//CURSOR(6, Itemstack.class, "clicked cursor", "cursor"); TODO this should be in a Skript slot type.
		
		private String name, syntax;
		private Class<?> clazz;
		private int value;

		private ClickableType(int value, Class<?> clazz, String name, String syntax) {
			this.syntax = syntax;
			this.value = value;
			this.clazz = clazz;
			this.name = name;
		}
		
		public int getValue() {
			return value;
		}
		
		public Class<?> getClickableClass() {
			return clazz;
		}
		
		public String getName() {
			return name;
		}
		
		public String getSyntax(Boolean last) {
			return value + "¦" + syntax + (!last ? "|" : "");
		}
		
		public static ClickableType getClickable(int num) {
			for (ClickableType clickable : ClickableType.values())
				if (clickable.getValue() == num) return clickable;
			return BLOCK_AND_ITEMS;
		}
	}
	
	static {
		Skript.registerExpression(ExprClicked.class, Object.class, ExpressionType.SIMPLE, "[the] clicked ("
					+ ClickableType.BLOCK_AND_ITEMS.getSyntax(false)
					+ ClickableType.SLOT.getSyntax(false)
					+ ClickableType.INVENTORY.getSyntax(false)
					+ ClickableType.TYPE.getSyntax(false)
					+ ClickableType.ACTION.getSyntax(true) + ")");
	}
	
	@Nullable
	private EntityData<?> entityType;
	@Nullable
	private ItemType itemType; //null results in any itemtype
	private ClickableType clickable = ClickableType.BLOCK_AND_ITEMS;
	private Boolean rawSlot = false;
	
	@Override
	public boolean init(final Expression<?>[] exprs, final int matchedPattern, final Kleenean isDelayed, final ParseResult parseResult) {
		clickable = ClickableType.getClickable(parseResult.mark);
		switch (clickable) {
			case BLOCK_AND_ITEMS:
				final Object type = exprs[0] == null ? null : ((Literal<?>) exprs[0]).getSingle();
				if (type instanceof EntityData) {
					entityType = (EntityData<?>) type;
					if (!ScriptLoader.isCurrentEvent(PlayerInteractEntityEvent.class) && !ScriptLoader.isCurrentEvent(PlayerInteractAtEntityEvent.class)) {
						Skript.error("The expression 'clicked entity' may only be used in a click event", ErrorQuality.SEMANTIC_ERROR);
						return false;
					}
				} else {
					itemType = (ItemType) type;
					if (!ScriptLoader.isCurrentEvent(PlayerInteractEvent.class)) {
						Skript.error("The expression 'clicked block' may only be used in a click event", ErrorQuality.SEMANTIC_ERROR);
						return false;
					}
				}
				break;
			case INVENTORY:
			case ACTION:
			case TYPE:
			case SLOT:
				if (clickable == ClickableType.SLOT && parseResult.expr.contains("raw")) rawSlot = true;
				if (!ScriptLoader.isCurrentEvent(InventoryClickEvent.class)) {
					Skript.error("The expression '" + clickable.getName() + "' may only be used in an inventory click event", ErrorQuality.SEMANTIC_ERROR);
					return false;
				}
				break;
		}
		return true;
	}
	
	@Override
	public boolean isSingle() {
		return true;
	}
	
	@Override
	public Class<? extends Object> getReturnType() {
		return (clickable != ClickableType.BLOCK_AND_ITEMS) ? clickable.getClickableClass() : entityType != null ? entityType.getType() : Block.class;
	}
	
	@SuppressWarnings("null")
	@Override
	@Nullable
	protected Object[] get(final Event event) {
		switch (clickable) {
			case BLOCK_AND_ITEMS:
				if (event instanceof PlayerInteractEvent) {
					if (entityType != null) //This is suppose to be null as this event should be for blocks
						return null;
					final Block block = ((PlayerInteractEvent) event).getClickedBlock();
					return (itemType == null || itemType.isOfType(block)) ? new Block[] {block} : null;
				} else if (event instanceof PlayerInteractEntityEvent) {
					if (entityType == null) //We're testing for the entity in this event
						return null;
					final Entity entity = ((PlayerInteractEntityEvent) event).getRightClicked();
					if (entityType.isInstance(entity)) {
						final Entity[] one = (Entity[]) Array.newInstance(entityType.getType(), 1);
						one[0] = entity;
						return one;
					}
					return null;
				}
				break;
			case TYPE:
				return new ClickType[] {((InventoryClickEvent) event).getClick()};
			case ACTION:
				return new InventoryAction[] {((InventoryClickEvent) event).getAction()};
			case INVENTORY:
				return new Inventory[] {((InventoryClickEvent) event).getClickedInventory()};
			case SLOT:
				return CollectionUtils.array((rawSlot) ? ((InventoryClickEvent) event).getRawSlot() : ((InventoryClickEvent) event).getSlot());
		}
		return null;
	}
	
	@Override
	public String toString(final @Nullable Event e, final boolean debug) {
		return "the " + (clickable != ClickableType.BLOCK_AND_ITEMS ? clickable.getName() : "clicked " + (entityType != null ? entityType : itemType != null ? itemType : "block"));
	}
	
}
