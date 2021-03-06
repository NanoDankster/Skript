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

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.eclipse.jdt.annotation.Nullable;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.RequiredPlugins;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;

@Name("Player List Header and Footer")
@Description("The message above and below the player list in the tab menu.")
@Examples({"set all players tab list header to \"Welcome to the Server!\"",
			"send \"%the player's tab list header%\" to player",
			"reset all player's tab list header"})
@Since("INSERT VERSION")
@RequiredPlugins("Spigot 1.13 or newer")
public class ExprPlayerlistHeaderFooter extends PropertyExpression<Player, String> {
	
	static {
		if (Skript.methodExists(Player.class, "setPlayerListHeaderFooter", String.class, String.class)) //This method is only present if the header and footer methods we use are
			Skript.registerExpression(ExprPlayerlistHeaderFooter.class, String.class, ExpressionType.PROPERTY, "[the] %players% (player|tab) list (1¦header|2¦footer)");
	}
	
	private static final int HEADER = 1, FOOTER = 2;
	
	private int mark;
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
		mark = parseResult.mark;
		setExpr((Expression<Player>) exprs[0]);
		return true;
	}
	
	@Override
	protected String[] get(Event e, Player[] source) {
		List<String> list = new ArrayList<>();
		for (Player player : source) {
			if (mark == HEADER) {
				list.add(player.getPlayerListHeader());
			} else if (mark == FOOTER) {
				list.add(player.getPlayerListFooter());
			}
		}
		return list.toArray(new String[list.size()]);
	}
	
	@Override
	@Nullable
	public Class<?>[] acceptChange(Changer.ChangeMode mode) {
		switch (mode) {
			case SET:
			case DELETE:
			case RESET:
				return CollectionUtils.array(String.class);
		}
		return null;
	}
	
	@Override
	public void change(Event e, @Nullable Object[] delta, Changer.ChangeMode mode) {
		final String text = delta == null ? "" : (String) delta[0];
		for (Player player : getExpr().getArray(e)) {
			if (mark == HEADER) {
				player.setPlayerListHeader(text);
			} else if (mark == FOOTER) {
				player.setPlayerListFooter(text);
			}
		}
	}
	
	@Override
	public Class<? extends String> getReturnType() {
		return String.class;
	}
	
	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return "the " + getExpr().toString(e, debug) + " player list " + (mark == HEADER ? "header" : mark == FOOTER ? "footer" : "");
	}
}
