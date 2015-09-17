package org.apache.pdfbox.pdmodel.interactive.action;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.common.filespecification.PDFileSpecification;

import java.io.IOException;

/**
 *
 * This represents a thread action that can be executed in a PDF document.
 *
 * @author Evgeniy Muravitskiy
 */
public class PDActionThread extends PDAction {

	/**
	 * This type of action this object represents.
	 */
	public static final String SUB_TYPE = "Thread";

	/**
	 * Default constructor.
	 */
	public PDActionThread()
	{
		super();
		setSubType( SUB_TYPE );
	}

	/**
	 * Constructor.
	 *
	 * @param a The action dictionary.
	 */
	public PDActionThread(COSDictionary a)
	{
		super( a );
	}

	/**
	 * @return The D entry of the specific thread action dictionary.
	 */
	// Dictionary, Integer or String.
	public COSBase getD()
	{
		return action.getDictionaryObject("D");
	}

	/**
	 * @param d The destination.
	 */

	public void setD(COSBase d)
	{
		action.setItem("D", d);
	}

	/**
	 * This will get the file in which the destination is located.
	 *
	 * @return The F entry of the specific thread action dictionary.
	 * @throws IOException If there is an error creating the file spec.
	 */
	public PDFileSpecification getFile() throws IOException
	{
		return PDFileSpecification.createFS(action.getDictionaryObject("F"));
	}

	/**
	 * This will set the file in which the destination is located.
	 *
	 * @param fs The file specification.
	 */
	public void setFile(PDFileSpecification fs)
	{
		action.setItem("F", fs);
	}

	/**
	 * @return The B entry of the specific thread action dictionary.
	 */
	// Dictionary or Integer.
	public COSBase getB()
	{
		return action.getDictionaryObject("B");
	}

	/**
	 * @param b The destination.
	 */
	public void setB(COSBase b)
	{
		action.setItem("D", b);
	}

}
