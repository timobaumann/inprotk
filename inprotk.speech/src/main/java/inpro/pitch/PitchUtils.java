/* 
 * Copyright 2007, 2008, 2009, Timo Baumann and the Inpro project
 * 
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 */

package inpro.pitch;

public class PitchUtils {

	public static final double CENT_CONST = 1731.2340490667560888319096172; // 1200 / ln(2)
	public static final double BY_CENT_CONST = 1 / CENT_CONST;
	
	public static double hzToCent(double hz) {
		return CENT_CONST * Math.log(hz / 110); 
	}
	
	public static double centToHz(double cent) {
		return Math.exp(cent * BY_CENT_CONST) * 110;
	}
	
}
