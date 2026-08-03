package gama.plugin.gamepad;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Strings;

import de.gurkenlabs.input4j.InputComponent;
import de.gurkenlabs.input4j.InputDevice;
import de.gurkenlabs.input4j.InputDevicePlugin;
import de.gurkenlabs.input4j.InputDevices;
import gama.annotations.action;
import gama.annotations.doc;
import gama.annotations.operator;
import gama.api.GAMA;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.GamaListFactory;
import gama.api.types.list.IList;

/**
 * GAML operators to read the state of the connected gamepads through the input4j library.
 *
 * <p>
 * Devices are discovered once when the class is loaded and kept up to date through input4j's
 * connect/disconnect listeners. Each operator is a static method, so its operands are its parameters
 * <em>except</em> the injected {@link IScope} (a static operator must keep at least one real operand,
 * which is why the listing operator takes a filter rather than no argument at all).
 * </p>
 *
 * <p>
 * Reading a live value ({@code gamepad_value}, {@code gamepad_pressed_buttons}) polls the device first,
 * so the returned values always reflect the current physical state of the controller. Connection and
 * disconnection are observed by comparing {@code gamepads("")} (or {@code gamepad_connected}) between steps.
 * </p>
 */
public class GamepadOperators {

	/** The input4j plugin, or {@code null} if input4j could not be initialised on this platform. */
	private static InputDevicePlugin _plugin;

	/** Connected devices, keyed by their stable id. Concurrent because input4j mutates it from listener threads. */
	private static final Map<String, InputDevice> _devices = new ConcurrentHashMap<>();

	static {
		try {
			_plugin = InputDevices.init();
			_plugin.getAll().forEach(d -> _devices.put(d.getID(), d));
			_plugin.onDeviceConnected(d -> _devices.put(d.getID(), d));
			_plugin.onDeviceDisconnected(d -> _devices.remove(d.getID()));
		} catch (final Throwable t) {
			// input4j may be unavailable: operators then simply
			// return empty / default values instead of breaking the simulation.
			_plugin = null;
		}
	}

	private GamepadOperators() {}

	@operator(
			value = "get_gamepads",
			category = { "Gamepad" },
			doc = @doc ("Returns the ids of the currently connected gamepads."))
	public static IList<String> gamepads(final IScope scope, final String filter) {
		if (_plugin != null) {
			return GamaListFactory.wrap(Types.STRING, _plugin.getAll().stream().map(d -> d.getID()).filter(n -> (Strings.isNullOrEmpty(filter) || n == null) ? true : n.contains(filter)).toList());
		}
		GAMA.reportAndThrowIfNeeded(scope, GamaRuntimeException.warning("input4j didn't load, gamepad plugin cannot work", scope), false);
		return null;
	}

	@operator (
			value = "gamepad_connected",
			category = { "Gamepad" },
			doc = @doc ("Returns whether a gamepad with the given id is currently connected."))
	public static boolean gamepadConnected(final IScope scope, final String id) {
		if (id == null) return false;
		return _devices.containsKey(id);
	}

	@operator (
			value = "gamepad_name",
			category = { "Gamepad" },
			doc = @doc ("Returns the human-readable display name of the gamepad with the given id (empty string if it is not connected)."))
	public static String gamepadName(final IScope scope, final String id) {
		if (id == null) return null;
		final InputDevice device = _devices.get(id);
		return device == null ? "" : device.getDisplayName();
	}

	@operator (
			value = "gamepad_components",
			category = { "Gamepad" },
			content_type = IType.STRING,
			doc = @doc ("Returns the names of all the components (buttons and axes) exposed by the gamepad with the given id."))
	public static IList<String> gamepadComponents(final IScope scope, final String id) {
		final IList<String> names = GamaListFactory.create(scope, Types.STRING);
		
		if (id == null) return names;
		
		final InputDevice device = _devices.get(id);
		if (device != null) {
			for (final InputComponent c : device.getComponents()) {
				names.add(c.getId().name);
			}
		}
		return names;
	}

	@operator (
			value = "gamepad_value",
			category = { "Gamepad" },
			doc = @doc ("Polls the gamepad with the given id and returns the current value of the named component: 0.0 or 1.0 for a button, usually a value in [-1.0, 1.0] for an axis. Returns 0.0 if the gamepad or the component is unknown."))
	public static double gamepadValue(final IScope scope, final String id, final String component) {
		
		if (id == null) return 0;
		
		final InputDevice device = _devices.get(id);
		if (device == null) return 0.0;
		device.poll();
		return device.getComponent(component).map(c -> (double) c.getData()).orElse(0.0);
	}

	@operator (
			value = "gamepad_pressed_buttons",
			category = { "Gamepad" },
			content_type = IType.STRING,
			doc = @doc ("Polls the gamepad with the given id and returns the names of the buttons that are currently pressed."))
	public static IList<String> gamepadPressedButtons(final IScope scope, final String id) {
		IList<String> pressed = GamaListFactory.create(scope, Types.STRING);
		
		if (id == null) return pressed;
		
		final InputDevice device = _devices.get(id);
		if (device != null) {
			device.poll();
			for (final InputComponent c : device.getComponents()) {
				if (c.isButton() && c.getData() > 0.5f) {
					pressed.add(c.getId().name);
				}
			}
		}
		return pressed;
	}

}
