model gamepads_tests


global{
	
	list<string> active_gamepads <- [];
	image gamepad_icon <- image('../includes/gamepad.png');
	
	
	
	reflex update_gamepad_list {
		
		list<string> new_list <- get_gamepads('');
		write "gamepads list: " + new_list;
		// we create the gamepads that were connected since last time
		loop gamepad_id over: new_list{
			if not (active_gamepads contains gamepad_id) {
				write "New gamepad detected: " + gamepad_id;
				create gamepad{
					id <- gamepad_id;
				}
			}
		}
		
		// we delete gamepads that are not detected anymore
		loop gamepad_id over:active_gamepads{
			if not (new_list contains gamepad_id){
				write "Gamepad " + gamepad_id + " was disconnected";
				ask first_with(gamepad, each.id = gamepad_id){
					do die();
				}
			}
		}
		
		active_gamepads <- new_list;
		
	}
	
}

species gamepad {
	
	string id;
	point dimensions <- point(15,10);
	
	aspect default{
		draw gamepad_icon size:dimensions;		
	}
}

experiment ex autorun:true{
	float minimum_cycle_duration <- 0.2#s;
	output {
		display main type:3d{
			species gamepad;
		}
	}
}