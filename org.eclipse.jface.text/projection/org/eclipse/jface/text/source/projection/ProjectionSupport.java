/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jface.text.source.projection;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyledTextContent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationPainter;
import org.eclipse.jface.text.source.IAnnotationAccess;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISharedTextColors;
import org.eclipse.jface.text.source.ISourceViewer;

/**
 * Supports the configuration of projection capabilities for projection viewers.
 * <p>
 * API in progress. Do not yet use.
 * 
 * @since 3.0
 */
public class ProjectionSupport {
	
	
	private static class ProjectionAnnotationsPainter extends AnnotationPainter {
		public ProjectionAnnotationsPainter(ISourceViewer sourceViewer, IAnnotationAccess access) {
			super(sourceViewer, access);
		}
		
		/*
		 * @see org.eclipse.jface.text.source.AnnotationPainter#isRepaintReason(int)
		 */
		protected boolean isRepaintReason(int reason) {
			return true;
		}
		
		/*
		 * @see org.eclipse.jface.text.source.AnnotationPainter#findAnnotationModel(org.eclipse.jface.text.source.ISourceViewer)
		 */
		protected IAnnotationModel findAnnotationModel(ISourceViewer sourceViewer) {
			if (sourceViewer instanceof ProjectionViewer) {
				ProjectionViewer projectionViewer= (ProjectionViewer) sourceViewer;
				return projectionViewer.getProjectionAnnotationModel();
			}
			return null;
		}
	}
	
	private static class ProjectionDrawingStrategy implements AnnotationPainter.IDrawingStrategy {
		/*
		 * @see org.eclipse.jface.text.source.AnnotationPainter.IDrawingStrategy#draw(org.eclipse.swt.graphics.GC, org.eclipse.swt.custom.StyledText, int, int, org.eclipse.swt.graphics.Color)
		 */
		public void draw(Annotation annotation, GC gc, StyledText textWidget, int offset, int length, Color color) {
			if (gc != null && annotation instanceof ProjectionAnnotation) {
				ProjectionAnnotation projectionAnnotation= (ProjectionAnnotation) annotation;
				if (projectionAnnotation.isCollapsed()) {
					
					StyledTextContent content= textWidget.getContent();
					int line= content.getLineAtOffset(offset);
					int lineStart= content.getOffsetAtLine(line);
					String text= content.getLine(line);
					int lineLength= text == null ? 0 : text.length();
					int lineEnd= lineStart + lineLength;
					Point p= textWidget.getLocationAtOffset(lineEnd);
					
					Color c= gc.getForeground();
					gc.setForeground(color);
					
					FontMetrics metrics= gc.getFontMetrics();
					int lineHeight= metrics.getHeight();
					int verticalMargin= lineHeight/10;
					int height= lineHeight - 2*verticalMargin;
					int width= metrics.getAverageCharWidth();
					gc.drawRectangle(p.x, p.y + verticalMargin, width, height);
					int third= width/3;
					int dotsVertical= p.y + metrics.getLeading() + metrics.getAscent();
					gc.drawPoint(p.x + third, dotsVertical);
					gc.drawPoint(p.x + 2*third, dotsVertical);
					
					gc.setForeground(c);
				}
			}
		}
	}
	
	private final static Object PROJECTION= new Object();
	
	private class ProjectionListener implements IProjectionListener {

		/*
		 * @see org.eclipse.jface.text.source.projection.IProjectionListener#projectionEnabled()
		 */
		public void projectionEnabled() {
			doEnableProjection();
		}
		
		/*
		 * @see org.eclipse.jface.text.source.projection.IProjectionListener#projectionDisabled()
		 */
		public void projectionDisabled() {
			doDisableProjection();
		}
	}
	
	private ProjectionViewer fViewer;
	private IAnnotationAccess fAnnotationAccess;
	private ISharedTextColors fSharedTextColors;
	private List fSummarizableTypes;
	private IAnnotationHover fAnnotationHover;
	private ProjectionListener fProjectionListener;
	private ProjectionAnnotationsPainter fPainter;
	private ProjectionRulerColumn fColumn;
	
	
	public ProjectionSupport(ProjectionViewer viewer, IAnnotationAccess annotationAccess, ISharedTextColors sharedTextColors) {
		fViewer= viewer;
		fAnnotationAccess= annotationAccess;
		fSharedTextColors= sharedTextColors;
	}
	
	public void addSummarizableAnnotationType(String annotationType) {
		if (fSummarizableTypes == null) {
			fSummarizableTypes= new ArrayList();
			fSummarizableTypes.add(annotationType);
		} else if (!fSummarizableTypes.contains(annotationType))
			fSummarizableTypes.add(annotationType);
	}
	
	public void removeSummarizableAnnotationType(String annotationType) {
		if (fSummarizableTypes != null)
			fSummarizableTypes.remove(annotationType);
		if (fSummarizableTypes.size() == 0)
			fSummarizableTypes= null;
	}
	
	public void setProjectionAnnotationHover(IAnnotationHover hover) {
		fAnnotationHover= hover;
	}
		
	public void install() {
		fViewer.setProjectionSummary(createProjectionSummary());
		
		doEnableProjection();
		
		fProjectionListener= new ProjectionListener();
		fViewer.addProjectionListener(fProjectionListener);
	}
	
	public void dispose() {
		if (fProjectionListener != null) {
			fViewer.removeProjectionListener(fProjectionListener);
			fProjectionListener= null;
		}
	}
	
	protected void doEnableProjection() {
		if (fPainter == null) {
			fPainter= new ProjectionAnnotationsPainter(fViewer, fAnnotationAccess);
			fPainter.addDrawingStrategy(PROJECTION, new ProjectionDrawingStrategy());
			fPainter.addAnnotationType(ProjectionAnnotation.TYPE, PROJECTION);
			fPainter.setAnnotationTypeColor(ProjectionAnnotation.TYPE, fSharedTextColors.getColor(getColor()));
		}
		fViewer.addPainter(fPainter);
		
		if (fColumn == null) {
			fColumn= new ProjectionRulerColumn(fViewer.getProjectionAnnotationModel(), 9, fAnnotationAccess);
			fColumn.addAnnotationType(ProjectionAnnotation.TYPE);
			fColumn.setHover(getProjectionAnnotationHover());
		}
		fViewer.addVerticalRulerColumn(fColumn);
	}
	
	protected void doDisableProjection() {
		if (fPainter != null) {
			fViewer.removePainter(fPainter);
			fPainter.dispose();
			fPainter= null;
		}
		
		if (fColumn != null) {
			fViewer.removeVerticalRulerColumn(fColumn);
			fColumn= null;
		}
	}
	
	private ProjectionSummary createProjectionSummary() {
		ProjectionSummary summary= new ProjectionSummary(fViewer, fAnnotationAccess);
		if (fSummarizableTypes != null) {
			int size= fSummarizableTypes.size();
			for (int i= 0; i < size; i++)
				summary.addAnnotationType((String) fSummarizableTypes.get(i));
		}
		return summary;
	}
	
	private IAnnotationHover getProjectionAnnotationHover() {
		if (fAnnotationHover == null)
			return new ProjectionAnnotationHover();
		return fAnnotationHover;
	}

	/**
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(ISourceViewer viewer, Class required) {
		if (ProjectionAnnotationModel.class.equals(required)) {
			if (viewer instanceof ProjectionViewer) {
				ProjectionViewer projectionViewer= (ProjectionViewer) viewer;
				return projectionViewer.getProjectionAnnotationModel();
			}
		}
		return null;
	}
	
	private RGB getColor() {
		// TODO read out preference settings
		Color c= Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY);
		return c.getRGB();
	}
}
