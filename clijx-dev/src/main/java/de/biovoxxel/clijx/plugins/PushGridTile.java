
package de.biovoxxel.clijx.plugins;

import org.scijava.plugin.Plugin;

import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.macro.CLIJHandler;
import net.haesleinhuepf.clij.macro.CLIJMacroPlugin;
import net.haesleinhuepf.clij.macro.CLIJOpenCLProcessor;
import net.haesleinhuepf.clij.macro.documentation.OffersDocumentation;
import net.haesleinhuepf.clij2.AbstractCLIJ2Plugin;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.utilities.HasAuthor;


/**
 * @author 	Jan Brocher (BioVoxxel)
 * 			January 2022
 *
 */

@Plugin(type = CLIJMacroPlugin.class, name = "CLIJ2_pushGridTile")
public class PushGridTile extends AbstractCLIJ2Plugin implements CLIJMacroPlugin, CLIJOpenCLProcessor, OffersDocumentation, HasAuthor {

	public boolean executeCL() {
		
		String imageName = (String) args[0];
		
		ImagePlus imp = WindowManager.getImage(imageName);
		
		if (imp == null) {
		    throw new IllegalArgumentException("You tried to push the image '" + args[0] + "' to the GPU.\n" +
		            "However, this image doesn't exist.");
		}
		
		int tileCountX = asInteger(args[1]);
		int tileCountY = asInteger(args[2]);
		int tileCountZ = asInteger(args[3]);
		int tileX = asInteger(args[4]);
		int tileY = asInteger(args[5]);
		int tileZ = asInteger(args[6]);
		float percentageOverlap = asFloat(args[7]);
					
		pushGridTile(getCLIJ2(), imp, tileCountX, tileCountY, tileCountZ, tileX, tileY, tileZ, percentageOverlap);
				
		return true;
	}

	
	public static ClearCLBuffer pushGridTile(CLIJ2 clij2, ImagePlus imp, Integer tileCountX, Integer tileCountY, Integer tileCountZ, Integer tileX, Integer tileY, Integer tileZ, Float percentageOverlap) {
		
		if (imp == null) {
			imp = WindowManager.getCurrentImage();
			if (imp == null) {
				throw new NullPointerException("The image you specified is not existing or there is no open image available.");
			}
		}
		System.out.println("Working on image = " + imp);
		
		if (percentageOverlap < 0) {
			percentageOverlap = 0.0f;
		}
		
		if (percentageOverlap > 99) {
			percentageOverlap = 99.0f;
		}
		
		
		float overlapFactor =  percentageOverlap / 100f;
		float nonOverlapFactor = 1f - overlapFactor;
		
		int imageWidth = imp.getWidth();
		int imageHeight = imp.getHeight();
		int imageDepth = imp.getNSlices();
		
		tileCountX = Math.max(1, tileCountX);
		tileCountY = Math.max(1, tileCountY);
		tileCountZ = Math.max(1, tileCountZ);
		
		
		if (tileCountZ > imageDepth) {
			tileCountZ = imageDepth;
		}

		int tileWidth = getTileSize(tileCountX, tileX, nonOverlapFactor, imageWidth);
		int tileHeight = getTileSize(tileCountY, tileY, nonOverlapFactor, imageHeight);
		int tileDepth = getTileSize(tileCountZ, tileZ, nonOverlapFactor, imageDepth);
		
		int x_overlap = (int) Math.floor(tileWidth * overlapFactor);
		int y_overlap = (int) Math.floor(tileHeight * overlapFactor);
		int z_overlap = (int) Math.floor(tileDepth * overlapFactor);
		
		System.out.println("x_overlap = " + x_overlap);
		System.out.println("y_overlap = " + y_overlap);
		System.out.println("z_overlap = " + z_overlap);
		
		ImageStack tileStack = imp.getImageStack().crop(tileX * tileWidth - tileX * x_overlap, tileY * tileHeight - tileY * y_overlap, tileZ * tileDepth - tileZ * z_overlap, tileWidth, tileHeight, tileDepth);
		
		ImagePlus tileImagePlus = new ImagePlus("current_tile", tileStack);
		
		System.out.println("Pushing " + tileImagePlus + " to GPU");
		ClearCLBuffer buffer = clij2.push(tileImagePlus);

		System.out.println(buffer);
		
		return buffer;
	}


	private static int getTileSize(Integer gridTileCount, Integer tilePositionID, float nonOverlapFactor, int imageSize) {
		int tileSize = (int) Math.floor(imageSize / (1 + (gridTileCount - 1) * nonOverlapFactor));
		//in case the current tile is the last one in this row / column / slice-block use the rest of the remaining image 
		if (tilePositionID == gridTileCount) {
			tileSize = imageSize - ((tilePositionID - 1) * tileSize);
		}
		System.out.println("tileWidth = " + tileSize);
		return tileSize;
	}
	
	
	//Not sure if that method is necessary at all???
	public static void pushGridTile(CLIJ2 clij2, ImagePlus imp, String imageName, Integer tileCountX, Integer tileCountY, Integer tileCountZ, Integer tileX, Integer tileY, Integer tileZ, Float percentageOverlap) {
		ClearCLBuffer buffer = pushGridTile(clij2, imp, tileCountX, tileCountY, tileCountZ, tileX, tileY, tileZ, percentageOverlap);
		CLIJHandler.getInstance().pushInternal(buffer, imageName);
	}
	
	
	@Override
    public ClearCLBuffer createOutputBufferFromSource(ClearCLBuffer input) {
		
		int imageWidth = (int) input.getWidth();
		int imageHeight = (int) input.getHeight();
		int imageDepth = (int) input.getDepth();
		
		float percentageOverlap = asFloat(args[7]);
		float overlapFactor =  percentageOverlap / 100f;
		float nonOverlapFactor = 1f - overlapFactor;
		
		int tileCountX = asInteger(args[1]);
		int tileCountY = asInteger(args[2]);
		int tileCountZ = asInteger(args[3]);
		int tileX = asInteger(args[4]);
		int tileY = asInteger(args[5]);
		int tileZ = asInteger(args[6]);
		
				
		int tileWidth = getTileSize(tileCountX, tileX, nonOverlapFactor, imageWidth);
		
		int tileHeight = getTileSize(tileCountY, tileY, nonOverlapFactor, imageHeight);
		
		int tileDepth = getTileSize(tileCountZ, tileZ, nonOverlapFactor, imageDepth);

        if (input.getDimension() == 2) {
            return getCLIJ2().create(new long[]{tileWidth, tileHeight}, input.getNativeType());
        } else {
            return getCLIJ2().create(new long[]{tileWidth, tileHeight, tileDepth}, input.getNativeType());
        }
    }
	
	

	public String getParameterHelpText() {
		return "String image, Number tileCountX, Number tileCountY, Number tileCountZ, Number tileX, Number tileY, Number tileZ, Number percentageOverlap";
	}

	


	public String getDescription() {
		return "Pushes a tile defined by its name and a grid specification (columns / rows / slice-blocks) together with a tile overlap percentage to GPU memory for further processing";
	}

	public String getAvailableForDimensions() {
		return "2D, 3D";
	}


	public String getAuthorName() {
		return "Jan Brocher";
	}


	
}
