run("CLIJ2 Macro Extensions", "cl_device=");
Ext.CLIJ2_clear();

run("Boats");
original = getTitle();

//Ext.CLIJ2_push(original);	//works
Ext.CLIJ2_pushGridTile(original, 4, 4, 1, 0, 0, 0, 20);	//does not work
Ext.CLIJ2_threshold(original, destination, 128);

Ext.CLIJ2_pull(destination);
Ext.CLIJ2_clear();

